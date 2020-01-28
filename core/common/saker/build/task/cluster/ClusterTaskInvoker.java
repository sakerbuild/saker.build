/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.task.cluster;

import java.util.Map;

import saker.build.file.content.ContentDatabaseImpl;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.FileMirrorHandler;
import saker.build.runtime.execution.ScriptAccessorClassPathData;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.repository.BuildRepository;
import saker.build.task.ComputationToken;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.IdentifierAccessDisablerSakerEnvironment;
import saker.build.task.InnerTaskInvokerInvocationManager;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationManager.ClusterExecutionEvent;
import saker.build.task.TaskInvocationManager.ExecutionEnvironmentSelectionEvent;
import saker.build.task.TaskInvocationManager.InnerClusterExecutionEvent;
import saker.build.task.TaskInvocationManager.InnerTaskInvocationHandle;
import saker.build.task.TaskInvocationManager.SelectionResult;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.task.TaskInvocationManager.TaskInvocationEvent;
import saker.build.task.TaskInvocationManager.TaskInvocationEventVisitor;
import saker.build.task.TaskInvoker;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;

public class ClusterTaskInvoker implements TaskInvoker {
	private SakerEnvironmentImpl environment;
	private IdentifierAccessDisablerSakerEnvironment suitableTesterSakerEnvironment;

	private ClusterExecutionContext clusterExecutionContext;
	private ContentDatabaseImpl clusterContentDatabase;

	private InnerTaskInvokerInvocationManager innerTaskInvoker;

	public ClusterTaskInvoker(SakerEnvironmentImpl environment, SakerEnvironment executionenvironment,
			ExecutionContext executioncontext, FileMirrorHandler mirrorhandler,
			Map<String, ? extends BuildRepository> loadedrepositories, ContentDatabaseImpl clustercontentdb,
			Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> loadedscriptlocators) {
		this.environment = environment;
		this.clusterContentDatabase = clustercontentdb;

		this.clusterExecutionContext = new ClusterExecutionContext(executionenvironment, executioncontext,
				loadedrepositories, mirrorhandler, loadedscriptlocators);

		this.suitableTesterSakerEnvironment = new IdentifierAccessDisablerSakerEnvironment(executionenvironment);

		this.innerTaskInvoker = new InnerTaskInvokerInvocationManager(clusterExecutionContext);
	}

	@Override
	public void run(TaskInvocationContext context) throws InterruptedException {
		ThreadUtils.setInheritableDefaultThreadFactor(environment.getThreadFactor());

		try (ThreadWorkPool workpool = ThreadUtils.newDynamicWorkPool("Cluster-worker-")) {
			while (true) {
				Iterable<TaskInvocationEvent> events = context.poll();
				if (events == null) {
					//exit
					return;
				}
				for (TaskInvocationEvent event : events) {
					workpool.offer(() -> {
						handleEvent(event);
					});
				}
			}
		}
	}

	private void handleEvent(TaskInvocationEvent event) {
		try {
			event.accept(new TaskInvocationEventVisitor() {
				@Override
				public void visit(ExecutionEnvironmentSelectionEvent event) {
					handleExecutionEnvironmentSelectionEvent(event);
				}

				@Override
				public void visit(ClusterExecutionEvent<?> event) {
					handleTaskExecutionEvent(event);
				}

				@Override
				public void visit(InnerClusterExecutionEvent<?> event) {
					handleInnerTaskExecutionEvent(event);
				}
			});
		} catch (RMIRuntimeException e) {
			//we can't really do anything with this
			//    however, we shouldn't throw it to the caller, as it is an isolated error to the given event.
			//    any hard failures will be received in the following poll() or other calls
			e.printStackTrace();
		}
	}

	protected <R> void handleInnerTaskExecutionEvent(InnerClusterExecutionEvent<R> event) {
		SelectionResult selectionresult = event.getSelectionResult();
		if (SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(suitableTesterSakerEnvironment,
				selectionresult.getQualifierEnvironmentProperties())) {
			event.failUnsuitable();
			return;
		}
		TaskContext realtaskcontext = event.getTaskContext();
		ClusterTaskContext clustertaskcontext = new ClusterTaskContext(clusterExecutionContext, realtaskcontext,
				this.clusterContentDatabase, event.getTaskUtilities());
		try {
			InnerTaskInvocationHandle<R> resulthandle = innerTaskInvoker.invokeInnerTask(event.getTaskFactory(),
					clustertaskcontext, event, realtaskcontext, event.getComputationTokenCount(),
					event.getDuplicationPredicate());
			event.setInvocationHandle(resulthandle);
		} catch (Exception e) {
			event.failInvocationStart(e);
		}
	}

	protected void handleExecutionEnvironmentSelectionEvent(ExecutionEnvironmentSelectionEvent event) {
		TaskExecutionEnvironmentSelector selector = event.getEnvironmentSelector();
		EnvironmentSelectionResult envselectionresult;
		try {
			envselectionresult = selector.isSuitableExecutionEnvironment(suitableTesterSakerEnvironment);
		} catch (Exception e) {
			event.failUnsuitable(e);
			return;
		}
		if (envselectionresult != null) {
			SelectionResult eventselectionresult = new SelectionResult(environment.getEnvironmentIdentifier(),
					envselectionresult.getQualifierEnvironmentProperties(), ImmutableUtils.makeImmutableHashSet(
							SakerEnvironmentImpl.getEnvironmentPropertyDifferences(suitableTesterSakerEnvironment,
									event.getDependentProperties()).keySet()));
			event.succeed(eventselectionresult);
		} else {
			event.failUnsuitable();
		}
	}

	//suppress unused computation token in try
	@SuppressWarnings("try")
	protected <R> void handleTaskExecutionEvent(ClusterExecutionEvent<R> event) {
		SelectionResult selectionresult = event.getSelectionResult();
		boolean hasdiffs = SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(suitableTesterSakerEnvironment,
				selectionresult.getQualifierEnvironmentProperties());
		if (hasdiffs) {
			//not suitable, don't attempt to invoke
			event.failUnsuitable();
			return;
		}
		int tokencount = event.getComputationTokenCount();
		TaskContext realtaskcontext = event.getTaskContext();
		//use the real task context as the computation token allocator, 
		//    so if other allocations are being made for the task, it will always succeed
		try (ComputationToken ctoken = ComputationToken.request(realtaskcontext, tokencount)) {
			if (!event.startExecution()) {
				return;
			}
			TaskFactory<R> factory = event.getTaskFactory();
			Task<? extends R> task = factory.createTask(clusterExecutionContext);
			if (task == null) {
				event.executionException(new NullPointerException(
						"TaskFactory " + factory.getClass().getName() + " created null Task."));
				return;
			}
			try {
				ClusterTaskContext clustertaskcontext = new ClusterTaskContext(clusterExecutionContext, realtaskcontext,
						clusterContentDatabase, event.getTaskUtilities());
				R taskres;
				try (TaskContextReference contextref = new TaskContextReference(clustertaskcontext)) {
					taskres = task.run(clustertaskcontext);
				} finally {
					ctoken.closeAll();
				}
				event.executionSuccessful(taskres);
			} catch (Exception e) {
				event.executionException(e);
			}
		} catch (InterruptedException e) {
			//failed to request computation tokens
			event.fail(e);
			return;
		}
	}

}
