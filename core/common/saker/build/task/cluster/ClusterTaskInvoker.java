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
import java.util.ServiceConfigurationError;

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
import saker.build.task.InnerTaskInvocationManager;
import saker.build.task.InternalTaskContext;
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
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;

public class ClusterTaskInvoker implements TaskInvoker {
	private SakerEnvironmentImpl environment;
	private IdentifierAccessDisablerSakerEnvironment suitableTesterSakerEnvironment;

	private ClusterExecutionContext clusterExecutionContext;
	private ContentDatabaseImpl clusterContentDatabase;

	private InnerTaskInvocationManager innerTaskInvoker;

	public ClusterTaskInvoker(SakerEnvironmentImpl environment, SakerEnvironment executionenvironment,
			ExecutionContext executioncontext, FileMirrorHandler mirrorhandler,
			Map<String, ? extends BuildRepository> loadedrepositories, ContentDatabaseImpl clustercontentdb,
			Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> loadedscriptlocators) {
		this.environment = environment;
		this.clusterContentDatabase = clustercontentdb;

		this.clusterExecutionContext = new ClusterExecutionContext(executionenvironment, executioncontext,
				loadedrepositories, mirrorhandler, loadedscriptlocators);

		this.suitableTesterSakerEnvironment = new IdentifierAccessDisablerSakerEnvironment(executionenvironment);

		this.innerTaskInvoker = new InnerTaskInvocationManager(clusterExecutionContext);
	}

	@Override
	public void run(TaskInvocationContext context) throws Exception {
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
		Throwable serialexc = event.getSerializationException();
		if (serialexc != null) {
			event.fail(serialexc);
			return;
		}

		if (SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(suitableTesterSakerEnvironment,
				event.getSelectionResult().getQualifierEnvironmentProperties())) {
			event.failUnsuitable();
			return;
		}
		TaskContext realtaskcontext = event.getTaskContext();
		ClusterTaskContext clustertaskcontext = new ClusterTaskContext(clusterExecutionContext, realtaskcontext,
				this.clusterContentDatabase, realtaskcontext.getTaskUtilities());
		try {
			InnerTaskInvocationHandle<R> resulthandle = innerTaskInvoker.invokeInnerTask(event.getTaskFactory(),
					clustertaskcontext, event, event.getComputationTokenCount(), event.getDuplicationPredicate(),
					event.getMaximumEnvironmentFactor());
			event.setInvocationHandle(resulthandle);
		} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
				| StackOverflowError e) {
			event.failInvocationStart(e);
		} catch (Throwable e) {
			try {
				event.failInvocationStart(e);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
	}

	protected void handleExecutionEnvironmentSelectionEvent(ExecutionEnvironmentSelectionEvent event) {
		Throwable serialexc = event.getSerializationException();
		if (serialexc != null) {
			event.fail(serialexc);
			return;
		}

		TaskExecutionEnvironmentSelector selector = event.getEnvironmentSelector();
		EnvironmentSelectionResult envselectionresult;
		try {
			envselectionresult = selector.isSuitableExecutionEnvironment(suitableTesterSakerEnvironment);
		} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
				| StackOverflowError e) {
			event.failUnsuitable(e);
			return;
		} catch (Throwable e) {
			try {
				event.failUnsuitable(e);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
		if (envselectionresult != null) {
			SelectionResult eventselectionresult = new SelectionResult(environment.getEnvironmentIdentifier(),
					envselectionresult.getQualifierEnvironmentProperties(), ImmutableUtils.makeImmutableHashSet(
							SakerEnvironmentImpl.getEnvironmentPropertyDifferences(suitableTesterSakerEnvironment,
									event.getDependentProperties()).keySet()));
			try {
				event.succeed(eventselectionresult);
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				//failed to write the success notification, or some other error happened, write as fail
				//this shouldn't happen, as the SelectionResult handles serialization failures by itself
				//but do this nonetheless to avoid possible halting due to no response
				event.fail(e);
			} catch (Throwable e) {
				try {
					event.fail(e);
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				throw e;
			}
		} else {
			event.failUnsuitable();
		}
	}

	//suppress unused resource in try
	@SuppressWarnings("try")
	protected <R> void handleTaskExecutionEvent(ClusterExecutionEvent<R> event) {
		Throwable serialexc = event.getSerializationException();
		if (serialexc != null) {
			event.fail(serialexc);
			return;
		}

		SelectionResult selectionresult = event.getSelectionResult();
		boolean hasdiffs = SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(suitableTesterSakerEnvironment,
				selectionresult.getQualifierEnvironmentProperties());
		if (hasdiffs) {
			//not suitable, don't attempt to invoke
			event.failUnsuitable();
			return;
		}
		TaskFactory<R> factory = event.getTaskFactory();
		Task<? extends R> task;
		try {
			task = factory.createTask(clusterExecutionContext);
		} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
				| StackOverflowError e) {
			event.fail(e);
			return;
		} catch (Throwable e) {
			try {
				event.fail(e);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
		if (task == null) {
			event.fail(new NullPointerException("TaskFactory " + factory.getClass().getName() + " created null Task."));
			return;
		}
		TaskContext realtaskcontext = event.getTaskContext();
		if (!(realtaskcontext instanceof InternalTaskContext)) {
			//safety check this here, later than later
			//as we're downcasting the task context
			event.fail(new IllegalArgumentException(
					"Invalid task context instance: " + ObjectUtils.classNameOf(realtaskcontext)));
			return;
		}
		ClusterTaskContext clustertaskcontext = new ClusterTaskContext(clusterExecutionContext, realtaskcontext,
				clusterContentDatabase, event.getTaskUtilities());

		int tokencount = event.getComputationTokenCount();

		//use the real task context as the computation token allocator, 
		//    so if other allocations are being made for the task, it will always succeed
		ComputationToken ctoken;
		try {
			ctoken = ComputationToken.request(realtaskcontext, tokencount);
		} catch (InterruptedException e) {
			//failed to request computation tokens
			event.fail(e);
			return;
		}
		try {
			if (!event.startExecution()) {
				return;
			}
			try {
				R taskres;
				InternalTaskBuildTrace btrace = clustertaskcontext.internalGetBuildTrace();
				try (TaskContextReference contextref = TaskContextReference.createForMainTask(clustertaskcontext)) {
					btrace.startTaskExecution();
					try {
						taskres = task.run(clustertaskcontext);
					} finally {
						btrace.endTaskExecution();
					}
				} finally {
					ctoken.closeAll();
				}
				event.executionSuccessful(taskres);
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				event.executionException(e);
			} catch (Throwable e) {
				try {
					event.executionException(e);
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				throw e;
			}
		} finally {
			ctoken.close();
		}
	}

}
