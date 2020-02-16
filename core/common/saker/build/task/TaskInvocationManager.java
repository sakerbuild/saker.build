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
package saker.build.task;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.TaskExecutionManager.ManagerInnerTaskResults;
import saker.build.task.TaskExecutionManager.TaskExecutorContext;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.task.cluster.TaskInvokerInformation;
import saker.build.task.exception.ClusterTaskExecutionFailedException;
import saker.build.task.exception.InnerTaskInitializationException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListWrapper;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;
import testing.saker.build.flag.TestFlag;

public class TaskInvocationManager implements Closeable {
	//TODO create cluster test in cases where the task factory fails to transfer
	public interface InnerTaskInvocationHandle<R> {
		public static final Method METHOD_CANCEL_DUPLICATION = ReflectUtils
				.getMethodAssert(InnerTaskInvocationHandle.class, "cancelDuplication");
		public static final Method METHOD_INTERRUPT = ReflectUtils.getMethodAssert(InnerTaskInvocationHandle.class,
				"interrupt");

		@RMISerialize
		public InnerTaskResultHolder<R> getResultIfPresent() throws InterruptedException;

		public void waitFinish() throws InterruptedException;

		public void cancelDuplication();

		public void interrupt();
	}

	public interface InnerTaskInvocationListener {
		public void notifyResultReady(boolean lastresult);

		public void notifyNoMoreResults();

		public boolean notifyTaskInvocationStart();
	}

	private SakerEnvironmentImpl environment;
	private ExecutionContextImpl executionContext;
	private SakerEnvironment localTesterEnvironment;

	private ThreadUtils.ThreadWorkPool invokerPool;
	private LazySupplier<?> clusterStartSupplier;
	private Collection<TaskInvocationContextImpl> invocationContexts;

	private volatile boolean closed = false;
	private Collection<? extends TaskInvokerFactory> invokerFactories;

	private InnerTaskInvokerInvocationManager innerTaskInvoker;

	public TaskInvocationManager(ExecutionContextImpl executioncontext,
			Collection<? extends TaskInvokerFactory> taskinvokerfactories, ThreadGroup clusterInteractionThreadGroup) {
		this.invokerFactories = taskinvokerfactories;
		this.environment = executioncontext.getRealEnvironment();
		this.localTesterEnvironment = new IdentifierAccessDisablerSakerEnvironment(executioncontext.getEnvironment());
		this.executionContext = executioncontext;
		this.clusterStartSupplier = LazySupplier.of(() -> {
			startClusters(clusterInteractionThreadGroup);
			return null;
		});

		this.innerTaskInvoker = new InnerTaskInvokerInvocationManager(executionContext);
	}

	//TODO the invoker selection results should be cached during the execution
	//     if two environment selector equals, no need for querying it twice
	//TODO we could modify the invoker selection by caching the environment properties locally
	//     we need to ensure that if the properties on the cluster gets invalidated, to invalidate the local cache as well
	//         this could be done by keeping an UUID (or some object) on the local side for invalidation versioning
	//         this version object could be queried when the cluster is being run()
	public Supplier<? extends SelectionResult> selectInvoker(TaskIdentifier taskid,
			TaskInvocationConfiguration configuration, Map<? extends EnvironmentProperty<?>, ?> dependentproperties,
			Set<UUID> allowedenvironmentids) {
		TaskExecutionEnvironmentSelector environmentselector = configuration.getEnvironmentSelector();
		boolean remotedispatchable = configuration.isRemoteDispatchable();

		return selectInvoker(taskid, dependentproperties, allowedenvironmentids, environmentselector,
				remotedispatchable);
	}

	public Supplier<? extends SelectionResult> selectInvoker(TaskIdentifier taskid,
			Map<? extends EnvironmentProperty<?>, ?> dependentproperties, Set<UUID> allowedenvironmentids,
			TaskExecutionEnvironmentSelector environmentselector, boolean remotedispatchable) {
		Exception localselectionexception = null;
		local_selection_block:
		if (allowedenvironmentids == null || allowedenvironmentids.contains(environment.getEnvironmentIdentifier())) {
			EnvironmentSelectionResult localselectionresult;
			try {
				localselectionresult = environmentselector.isSuitableExecutionEnvironment(localTesterEnvironment);
			} catch (Exception e) {
				localselectionexception = e;
				break local_selection_block;
			}
			if (localselectionresult != null) {
				return Functionals.valSupplier(new SelectionResult(environment.getEnvironmentIdentifier(),
						localselectionresult.getQualifierEnvironmentProperties(),
						ImmutableUtils.makeImmutableHashSet(SakerEnvironmentImpl
								.getEnvironmentPropertyDifferences(localTesterEnvironment, dependentproperties)
								.keySet())));
			}
		}

		if (!remotedispatchable || ObjectUtils.isNullOrEmpty(invokerFactories)) {
			final Exception finallocalselectionexception = localselectionexception;
			return () -> {
				throw new TaskEnvironmentSelectionFailedException(finallocalselectionexception);
			};
		}
		ensureClustersStarted();

		InvokerSelectionFutureSupplier result = new InvokerSelectionFutureSupplier(localselectionexception);
		ExecutionEnvironmentInvocationRequestImpl request = new ExecutionEnvironmentInvocationRequestImpl(
				invocationContexts, environmentselector, dependentproperties, result);

		if (allowedenvironmentids == null) {
			for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
				invocationcontext.addEvent(new ExecutionEnvironmentSelectionEventImpl(invocationcontext, request));
			}
		} else {
			for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
				if (!allowedenvironmentids.contains(invocationcontext.getEnvironmentIdentifier())) {
					continue;
				}
				invocationcontext.addEvent(new ExecutionEnvironmentSelectionEventImpl(invocationcontext, request));
			}
		}
		return result;
	}

	public static class TaskInvocationResult<R> {
		private final Optional<R> result;
		private final Throwable thrownException;

		public TaskInvocationResult(Optional<R> result, Throwable thrownException) {
			this.result = result;
			this.thrownException = thrownException;
		}

		public Optional<R> getResult() {
			return result;
		}

		public Throwable getThrownException() {
			return thrownException;
		}
	}

	//suppress unused TaskContextReference
	@SuppressWarnings("try")
	public <R> TaskInvocationResult<R> invokeTaskRunning(TaskFactory<R> factory,
			TaskInvocationConfiguration capabilities, SelectionResult selectionresult,
			TaskExecutorContext<R> taskcontext) throws InterruptedException, ClusterTaskExecutionFailedException {
		//TODO if the given number of tokens are available from the start, try request it and run on the local
		//     machine if available
		if (capabilities.isRemoteDispatchable() && !ObjectUtils.isNullOrEmpty(invokerFactories)) {
			ensureClustersStarted();

			TaskExecutionRequestImpl<R> request = new TaskExecutionRequestImpl<>(invocationContexts, factory,
					capabilities, selectionresult, taskcontext);
			for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
				invocationcontext.addEvent(new TaskClusterExecutionEventImpl<>(invocationcontext, request));
			}
			//XXX we shouldn't wait for all clusters to respond, as if we can invoke the task locally, 
			//    it will be bottlenecked by the slowest cluster
			request.waitForResult();
			TaskInvocationResult<R> invocationresult = request.toTaskInvocationResult();
			if (invocationresult != null) {
				return invocationresult;
			}
			//all clusters failed to invoke
			//proceed with invoking on the local if possible
		}
		boolean hasdiffs = SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(localTesterEnvironment,
				selectionresult.getQualifierEnvironmentProperties());
		if (hasdiffs) {
			//either at least one of the cluster is suitable
			//or the local is
			//if we reach here, either the suitable cluster hasn't responded
			//or the local environment is not suitable
			throw new ClusterTaskExecutionFailedException("Suitable execution environment is no longer accessible.");
		}
		int tokencount = capabilities.getComputationTokenCount();
		try (ComputationToken ctoken = ComputationToken.request(taskcontext, tokencount)) {
			Task<? extends R> task = factory.createTask(executionContext);
			if (task == null) {
				return new TaskInvocationResult<>(null, new NullPointerException(
						"TaskFactory " + factory.getClass().getName() + " created null Task."));
			}
			R taskres;
			InternalTaskBuildTrace btrace = taskcontext.internalGetBuildTrace();
			try (TaskContextReference contextref = new TaskContextReference(taskcontext, btrace)) {
				btrace.startTaskExecution();
				taskres = task.run(taskcontext);
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				return new TaskInvocationResult<>(null, e);
			} finally {
				btrace.endTaskExecution();
				ctoken.closeAll();
			}
			return new TaskInvocationResult<>(Optional.ofNullable(taskres), null);
		}
	}

	public <R> ManagerInnerTaskResults<R> invokeInnerTaskRunning(TaskFactory<R> taskfactory,
			SelectionResult selectionresult, int duplicationfactor, boolean duplicationcancellable,
			TaskContext taskcontext, TaskDuplicationPredicate duplicationPredicate,
			TaskInvocationConfiguration configuration, Set<UUID> allowedenvironmentids) {
		ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationhandles = new ConcurrentLinkedQueue<>();
		final Object resultwaiterlock = new Object();
		Exception invokerexceptions = null;
		int computationtokencount = configuration.getComputationTokenCount();

		Predicate<UUID> posttoenvironmentpredicate = selectionresult.environmentId::equals;
		if (duplicationfactor < 0 || duplicationfactor > 1) {
			//the inner task is remote dispatchable
			if (allowedenvironmentids == null) {
				posttoenvironmentpredicate = Functionals.alwaysPredicate();
			} else {
				posttoenvironmentpredicate = posttoenvironmentpredicate.or(allowedenvironmentids::contains);
			}
		} else {
			//run only on a single environment
			//that is the environment of the selection result
		}

		if (TestFlag.ENABLED) {
			if (configuration.isRemoteDispatchable()
					&& TestFlag.metric().isForceInnerTaskClusterInvocation(taskfactory)) {
				if (selectionresult.environmentId.equals(environment.getEnvironmentIdentifier())) {
					//if the selection result is the same as the environment, reset the predicate, as that may only
					//include the local environment 
					posttoenvironmentpredicate = u -> !u.equals(environment.getEnvironmentIdentifier());
				} else {
					posttoenvironmentpredicate = posttoenvironmentpredicate
							.and(u -> !u.equals(environment.getEnvironmentIdentifier()));
				}
			}
		}

		//TODO handle if all the clusters fail, and throw a proper initialization exception at the right time

		InvokerTaskResultsHandler<R> fut = new InvokerTaskResultsHandler<>(invocationhandles, resultwaiterlock,
				duplicationcancellable);

		if (posttoenvironmentpredicate.test(environment.getEnvironmentIdentifier())) {
			boolean hasdiffs = SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(localTesterEnvironment,
					selectionresult.getQualifierEnvironmentProperties());
			if (!hasdiffs) {
				ListenerInnerTaskInvocationHandler<R> listener = new ListenerInnerTaskInvocationHandler<>(
						resultwaiterlock, taskcontext);
				try {
					InnerTaskInvocationHandle<R> handle = innerTaskInvoker.invokeInnerTask(taskfactory, taskcontext,
							listener, taskcontext, computationtokencount, duplicationPredicate);
					listener.handle = handle;
					invocationhandles.add(listener);
				} catch (Exception e) {
					invokerexceptions = IOUtils.addExc(invokerexceptions, e);
				}
			}
		}

		if (configuration.isRemoteDispatchable()) {
			ensureClustersStarted();
			InnerTaskExecutionRequestImpl<R> request = new InnerTaskExecutionRequestImpl<>(invocationContexts, fut,
					computationtokencount, taskfactory, taskcontext, duplicationPredicate, selectionresult,
					duplicationfactor);
			for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
				if (!posttoenvironmentpredicate.test(invocationcontext.getEnvironmentIdentifier())) {
					continue;
				}
				ListenerInnerTaskInvocationHandler<R> listener = new ListenerInnerTaskInvocationHandler<>(
						resultwaiterlock, taskcontext);
				InnerClusterExecutionEventImpl<R> eventimpl = new InnerClusterExecutionEventImpl<>(invocationcontext,
						request, listener);
				listener.handle = eventimpl;
				invocationcontext.addEvent(eventimpl);
				invocationhandles.add(listener);
			}
		}

		if (invocationhandles.isEmpty()) {
			if (invokerexceptions != null) {
				throw new InnerTaskInitializationException(
						"Failed to start the inner task on any of the invocation environments.", invokerexceptions);
			}
		}
		if (invokerexceptions != null) {
			taskcontext.getTaskUtilities().reportIgnoredException(invokerexceptions);
		}

		return fut;
	}

	private void ensureClustersStarted() {
		clusterStartSupplier.get();
	}

	private void startClusters(ThreadGroup clusterInteractionThreadGroup) {
		this.invokerPool = ThreadUtils.newDynamicWorkPool(clusterInteractionThreadGroup, "Task-invoker-");
		List<TaskInvocationContextImpl> invocationcontexts = new ArrayList<>();
		invocationContexts = invocationcontexts;
		TaskInvokerInformation taskinvokerinformation = new TaskInvokerInformation(
				LocalFileProvider.getProviderKeyStatic(), executionContext.getDatabaseConfiguretion());

		for (TaskInvokerFactory invokerfactory : invokerFactories) {
			TaskInvocationContextImpl taskinvocationcontext = new TaskInvocationContextImpl(
					invokerfactory.getEnvironmentIdentifier());
			invocationcontexts.add(taskinvocationcontext);
			this.invokerPool.offer(() -> {
				TaskInvoker pullingtaskinvoker = invokerfactory.createTaskInvoker(executionContext,
						taskinvokerinformation);
				taskinvocationcontext.run(pullingtaskinvoker);
			});
		}
	}

	@Override
	public void close() {
		closed = true;
		clusterStartSupplier.getIfComputedPrevent();
		if (invocationContexts != null) {
			IllegalStateException cause = new IllegalStateException("Invocation manager closed.");
			for (TaskInvocationContextImpl ic : invocationContexts) {
				ic.close(cause);
			}
		}
		ThreadWorkPool invokerpool = invokerPool;
		if (invokerpool != null) {
			try {
				invokerpool.close();
			} catch (Exception e) {
				executionContext.reportIgnoredException(e);
			}
		}
	}

	public static class SelectionResult implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected UUID environmentId;
		protected Map<? extends EnvironmentProperty<?>, ?> qualifierEnvironmentProperties;
		protected Set<? extends EnvironmentProperty<?>> modifiedEnvironmentProperties;

		/**
		 * For {@link Externalizable}.
		 */
		public SelectionResult() {
		}

		public SelectionResult(UUID environmentId,
				Map<? extends EnvironmentProperty<?>, ?> qualifierEnvironmentProperties,
				Set<? extends EnvironmentProperty<?>> modifiedEnvironmentProperties) {
			this.environmentId = environmentId;
			this.qualifierEnvironmentProperties = qualifierEnvironmentProperties;
			this.modifiedEnvironmentProperties = modifiedEnvironmentProperties;
		}

		public Map<? extends EnvironmentProperty<?>, ?> getQualifierEnvironmentProperties() {
			return qualifierEnvironmentProperties;
		}

		public Set<? extends EnvironmentProperty<?>> getModifiedEnvironmentProperties() {
			return modifiedEnvironmentProperties;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(environmentId);
			SerialUtils.writeExternalMap(out, qualifierEnvironmentProperties);
			SerialUtils.writeExternalCollection(out, modifiedEnvironmentProperties);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			environmentId = (UUID) in.readObject();
			qualifierEnvironmentProperties = SerialUtils.readExternalImmutableHashMap(in);
			modifiedEnvironmentProperties = SerialUtils.readExternalImmutableHashSet(in);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[modifiedEnvironmentProperties=" + modifiedEnvironmentProperties
					+ ", qualifierEnvironmentProperties=" + qualifierEnvironmentProperties + "]";
		}
	}

	public interface TaskInvocationContext {
		@RMIWrap(RMIArrayListWrapper.class)
		public Iterable<TaskInvocationEvent> poll() throws InterruptedException;
	}

	public interface InvocationRequest {
		public boolean isActive();

		public void fail(TaskInvocationContext invocationcontext, @RMISerialize Throwable cause);
	}

	public interface TaskInvocationEventVisitor {
		public void visit(ExecutionEnvironmentSelectionEvent event);

		public void visit(ClusterExecutionEvent<?> event);

		public void visit(InnerClusterExecutionEvent<?> event);
	}

	public interface TaskInvocationEvent {
		public boolean isActive();

		public void fail(@RMISerialize Throwable cause);

		public void accept(TaskInvocationEventVisitor visitor);
	}

	public interface ExecutionEnvironmentSelectionEvent extends TaskInvocationEvent {
		public static final Method METHOD_FAILUNSUITABLE = ReflectUtils
				.getMethodAssert(ExecutionEnvironmentSelectionEvent.class, "failUnsuitable");

		@RMISerialize
		@RMICacheResult
		public TaskExecutionEnvironmentSelector getEnvironmentSelector();

		@RMISerialize
		@RMICacheResult
		public Map<? extends EnvironmentProperty<?>, ?> getDependentProperties();

		public void succeed(@RMISerialize SelectionResult result);

		public void failUnsuitable();

		public void failUnsuitable(@RMISerialize Exception e);
	}

	public interface ClusterExecutionEvent<R> extends TaskInvocationEvent {
		public static final Method METHOD_FAILUNSUITABLE = ReflectUtils.getMethodAssert(ClusterExecutionEvent.class,
				"failUnsuitable");

		@RMICacheResult
		public int getComputationTokenCount();

		@RMISerialize
		@RMICacheResult
		public TaskFactory<R> getTaskFactory();

		@RMISerialize
		@RMICacheResult
		public SelectionResult getSelectionResult();

		@RMICacheResult
		public TaskContext getTaskContext();

		@RMICacheResult
		public TaskExecutionUtilities getTaskUtilities();

		public boolean startExecution();

		public void executionSuccessful(@RMISerialize R taskresult);

		public void executionException(@RMISerialize Throwable e);

		public void failUnsuitable();

	}

	public interface InnerClusterExecutionEvent<R> extends TaskInvocationEvent, InnerTaskInvocationListener {
		public static final Method METHOD_FAILUNSUITABLE = ReflectUtils
				.getMethodAssert(InnerClusterExecutionEvent.class, "failUnsuitable");
		public static final Method METHOD_SETINVOCATIONHANDLE = ReflectUtils.getMethodAssert(
				InnerClusterExecutionEvent.class, "setInvocationHandle", InnerTaskInvocationHandle.class);

		@RMICacheResult
		public int getComputationTokenCount();

		@RMISerialize
		@RMICacheResult
		public TaskFactory<R> getTaskFactory();

		@RMICacheResult
		public TaskContext getTaskContext();

		@RMICacheResult
		public TaskExecutionUtilities getTaskUtilities();

		@RMICacheResult
		public TaskDuplicationPredicate getDuplicationPredicate();

		public void setInvocationHandle(InnerTaskInvocationHandle<R> resulthandle);

		public void failInvocationStart(@RMISerialize Exception e);

		@RMISerialize
		@RMICacheResult
		public SelectionResult getSelectionResult();

		public void failUnsuitable();
	}

	private abstract static class BaseInvocationRequest implements InvocationRequest {
		private Set<TaskInvocationContext> failedContexts = ConcurrentHashMap.newKeySet();

		public BaseInvocationRequest(Collection<? extends TaskInvocationContext> invocationcontexts) {
			failedContexts.addAll(invocationcontexts);
		}

		@Override
		public boolean isActive() {
			return !failedContexts.isEmpty();
		}

		@Override
		public void fail(TaskInvocationContext invocationcontext, Throwable cause) {
			if (failedContexts.remove(invocationcontext) && failedContexts.isEmpty()) {
				allClustersFailed();
			}
		}

		protected abstract void allClustersFailed();

		public boolean isAllClustersFailed() {
			return failedContexts.isEmpty();
		}
	}

	private static class ExecutionEnvironmentInvocationRequestImpl extends BaseInvocationRequest {
		private final TaskExecutionEnvironmentSelector environmentSelector;
		private final Map<? extends EnvironmentProperty<?>, ?> dependentProperties;
		private final InvokerSelectionFutureSupplier supplierResult;

		public ExecutionEnvironmentInvocationRequestImpl(Collection<? extends TaskInvocationContext> invocationcontexts,
				TaskExecutionEnvironmentSelector environmentSelector,
				Map<? extends EnvironmentProperty<?>, ?> dependentProperties,
				InvokerSelectionFutureSupplier supplierResult) {
			super(invocationcontexts);
			this.environmentSelector = environmentSelector;
			this.dependentProperties = dependentProperties;
			this.supplierResult = supplierResult;
		}

		@Override
		public boolean isActive() {
			return !supplierResult.hasResult() && super.isActive();
		}

		public TaskExecutionEnvironmentSelector getEnvironmentSelector() {
			return environmentSelector;
		}

		public Map<? extends EnvironmentProperty<?>, ?> getDependentProperties() {
			return dependentProperties;
		}

		@Override
		protected void allClustersFailed() {
			supplierResult.fail();
		}

		public void succeed(SelectionResult result) {
			supplierResult.setResult(Functionals.valSupplier(result));
		}

	}

	private static class InnerTaskExecutionRequestImpl<R> extends BaseInvocationRequest {
		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<TaskInvocationManager.InnerTaskExecutionRequestImpl> AIFU_duplicationCount = AtomicIntegerFieldUpdater
				.newUpdater(TaskInvocationManager.InnerTaskExecutionRequestImpl.class, "duplicationCount");
		private volatile int duplicationCount;

		private final InvokerTaskResultsHandler<R> resultsHandler;
		private final int computationTokenCount;
		private final TaskFactory<R> taskFactory;
		private final TaskContext taskContext;
		private final TaskExecutionUtilities taskUtilities;
		private final TaskDuplicationPredicate duplicationPredicate;
		private final SelectionResult selectionResult;

		public InnerTaskExecutionRequestImpl(Collection<? extends TaskInvocationContext> invocationcontexts,
				InvokerTaskResultsHandler<R> resultsHandler, int computationTokenCount, TaskFactory<R> taskFactory,
				TaskContext taskContext, TaskDuplicationPredicate duplicationPredicate, SelectionResult selectionResult,
				int duplicationfactor) {
			super(invocationcontexts);
			this.resultsHandler = resultsHandler;
			this.computationTokenCount = computationTokenCount;
			this.taskFactory = taskFactory;
			this.taskContext = taskContext;
			this.duplicationPredicate = duplicationPredicate;
			this.selectionResult = selectionResult;
			this.duplicationCount = duplicationfactor == 0 ? 1 : duplicationfactor;

			this.taskUtilities = taskContext.getTaskUtilities();
		}

		public void failInit(TaskInvocationContext invocationcontext, Throwable e) {
			super.fail(invocationcontext, e);
		}

		@Override
		public boolean isActive() {
			return duplicationCount != 0 && super.isActive();
		}

		@Override
		protected void allClustersFailed() {
			resultsHandler.allClustersFailed();
		}

		public TaskExecutionUtilities getTaskUtilities() {
			return taskUtilities;
		}

		public int getComputationTokenCount() {
			return computationTokenCount;
		}

		public TaskContext getTaskContext() {
			return taskContext;
		}

		public TaskFactory<R> getTaskFactory() {
			return taskFactory;
		}

		public TaskDuplicationPredicate getDuplicationPredicate() {
			return duplicationPredicate;
		}

		public SelectionResult getSelectionResult() {
			return selectionResult;
		}

		public void duplicated() {
			while (true) {
				int c = duplicationCount;
				if (c <= 0) {
					break;
				}
				if (AIFU_duplicationCount.compareAndSet(this, c, c - 1)) {
					break;
				}
			}
		}
	}

	private static class TaskExecutionRequestImpl<R> extends BaseInvocationRequest {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskInvocationManager.TaskExecutionRequestImpl, TaskInvocationContext> ARFU_starterInvocationContext = AtomicReferenceFieldUpdater
				.newUpdater(TaskInvocationManager.TaskExecutionRequestImpl.class, TaskInvocationContext.class,
						"starterInvocationContext");

		private volatile TaskInvocationContext starterInvocationContext;

		private final TaskFactory<R> factory;
		private final TaskInvocationConfiguration capabilities;
		private final SelectionResult selectionResult;
		private final TaskContext taskContext;
		private final TaskExecutionUtilities taskUtilities;

		private R result;
		private Throwable resultException;
		private volatile boolean finished = false;

		public TaskExecutionRequestImpl(Collection<TaskInvocationContextImpl> invocationContexts,
				TaskFactory<R> factory, TaskInvocationConfiguration capabilities, SelectionResult selectionresult,
				TaskContext taskcontext) {
			super(invocationContexts);
			this.factory = factory;
			this.capabilities = capabilities;
			this.selectionResult = selectionresult;
			this.taskContext = taskcontext;
			this.taskUtilities = taskcontext.getTaskUtilities();
		}

		@Override
		public boolean isActive() {
			return starterInvocationContext == null && super.isActive();
		}

		@Override
		protected void allClustersFailed() {
			finished = true;
			synchronized (this) {
				this.notifyAll();
			}
		}

		public void executionSuccessful(R taskresult) {
			finished = true;
			result = taskresult;
			synchronized (this) {
				this.notifyAll();
			}
		}

		public void executionException(Throwable e) {
			finished = true;
			resultException = e;
			synchronized (this) {
				this.notifyAll();
			}
		}

		@Override
		public void fail(TaskInvocationContext invocationcontext, Throwable cause) {
			if (isStartedExecution(invocationcontext)) {
				failWithStartedExecution(invocationcontext, cause);
			} else {
				super.fail(invocationcontext, cause);
			}
		}

		private void failWithStartedExecution(TaskInvocationContext invocationContext, Throwable cause) {
			finished = true;
			resultException = new ClusterTaskExecutionFailedException(cause);
			synchronized (this) {
				this.notifyAll();
			}
		}

		public TaskInvocationConfiguration getCapabilities() {
			return capabilities;
		}

		public TaskFactory<R> getFactory() {
			return factory;
		}

		public SelectionResult getSelectionResult() {
			return selectionResult;
		}

		public TaskContext getTaskContext() {
			return taskContext;
		}

		public TaskExecutionUtilities getTaskUtilities() {
			return taskUtilities;
		}

		public boolean startExecution(TaskInvocationContext invocationContext) {
			if (!ARFU_starterInvocationContext.compareAndSet(this, null, invocationContext)) {
				//the given execution context will not start the execution
				super.fail(invocationContext, null);
				return false;
			}
			return true;
		}

		public boolean isStartedExecution(TaskInvocationContext invocationcontext) {
			return starterInvocationContext == invocationcontext;
		}

		public void waitForResult() throws InterruptedException {
			synchronized (this) {
				while (true) {
					if (this.finished) {
						return;
					}
					this.wait();
				}
			}
		}

		public Optional<R> getResultOptional() {
			if (resultException == null) {
				return Optional.ofNullable(result);
			}
			return null;
		}

		public TaskInvocationResult<R> toTaskInvocationResult() {
			if (isAllClustersFailed()) {
				return null;
			}
			return new TaskInvocationResult<>(getResultOptional(), resultException);
		}

	}

	@RMIWrap(ExecutionEnvironmentSelectionEventRMIWrapper.class)
	private static class ExecutionEnvironmentSelectionEventRMIWrapper
			implements RMIWrapper, ExecutionEnvironmentSelectionEvent {
		private ExecutionEnvironmentSelectionEvent event;

		private TaskExecutionEnvironmentSelector environmentSelector;
		private Map<? extends EnvironmentProperty<?>, ?> dependentProprties;

		@SuppressWarnings("unused")
		//used by the RMI runtime
		public ExecutionEnvironmentSelectionEventRMIWrapper() {
		}

		@SuppressWarnings("unused")
		//used by the RMI runtime
		public ExecutionEnvironmentSelectionEventRMIWrapper(ExecutionEnvironmentSelectionEvent event) {
			this.event = event;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(event);
			out.writeSerializedObject(event.getEnvironmentSelector());
			out.writeSerializedObject(event.getDependentProperties());
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			event = (ExecutionEnvironmentSelectionEvent) in.readObject();
			environmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
			dependentProprties = (Map<? extends EnvironmentProperty<?>, ?>) in.readObject();
		}

		@Override
		public Object resolveWrapped() {
			return this;
		}

		@Override
		public Object getWrappedObject() {
			return event;
		}

		@Override
		public boolean isActive() {
			return event.isActive();
		}

		@Override
		public void failUnsuitable() {
			callRMIAsyncAssert(event, METHOD_FAILUNSUITABLE);
		}

		@Override
		public void failUnsuitable(Exception e) {
			try {
				event.failUnsuitable(e);
			} catch (RMIIOFailureException e2) {
				//we cannot recover from IO failures
				throw e2;
			} catch (RMIRuntimeException e2) {
				//the method call may fail due to object transfer failures
				//if the exception argument cannot be transferred, then fall back to the normal
				//unsuitability notification
				failUnsuitable();
			}
		}

		@Override
		public void fail(Throwable cause) {
			event.fail(cause);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public TaskExecutionEnvironmentSelector getEnvironmentSelector() {
			return environmentSelector;
		}

		@Override
		public void succeed(SelectionResult result) {
			event.succeed(result);
		}

		@Override
		public Map<? extends EnvironmentProperty<?>, ?> getDependentProperties() {
			return dependentProprties;
		}

	}

	@RMIWrap(ExecutionEnvironmentSelectionEventRMIWrapper.class)
	private static class ExecutionEnvironmentSelectionEventImpl implements ExecutionEnvironmentSelectionEvent {
		private final TaskInvocationContext invocationContext;
		private final ExecutionEnvironmentInvocationRequestImpl request;

		public ExecutionEnvironmentSelectionEventImpl(TaskInvocationContext invocationContext,
				ExecutionEnvironmentInvocationRequestImpl request) {
			this.invocationContext = invocationContext;
			this.request = request;
		}

		@Override
		public boolean isActive() {
			return request.isActive();
		}

		@Override
		public TaskExecutionEnvironmentSelector getEnvironmentSelector() {
			return request.getEnvironmentSelector();
		}

		@Override
		public void succeed(SelectionResult result) {
			request.succeed(result);
		}

		@Override
		public void fail(Throwable cause) {
			request.fail(invocationContext, cause);
		}

		@Override
		public void failUnsuitable() {
			request.fail(invocationContext, null);
		}

		@Override
		public void failUnsuitable(Exception e) {
			request.fail(invocationContext, e);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public Map<? extends EnvironmentProperty<?>, ?> getDependentProperties() {
			return request.getDependentProperties();
		}
	}

	@RMIWrap(InnerClusterExecutionEventRMIWrapper.class)
	private static class InnerClusterExecutionEventRMIWrapper<R> implements RMIWrapper, InnerClusterExecutionEvent<R> {
		private InnerClusterExecutionEvent<R> event;
		private int computationTokenCount;
		private TaskFactory<R> taskFactory;
		private TaskContext taskContext;
		private TaskDuplicationPredicate duplicationPredicate;
		private SelectionResult selectionResult;
		private TaskExecutionUtilities taskUtilities;

		@SuppressWarnings("unused")
		//used by the RMI runtime
		public InnerClusterExecutionEventRMIWrapper() {
		}

		@SuppressWarnings("unused")
		//used by the RMI runtime
		public InnerClusterExecutionEventRMIWrapper(InnerClusterExecutionEvent<R> event) {
			this.event = event;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(event);
			out.writeInt(event.getComputationTokenCount());
			out.writeSerializedObject(event.getTaskFactory());
			out.writeRemoteObject(event.getTaskContext());
			out.writeRemoteObject(event.getTaskUtilities());
			out.writeObject(event.getDuplicationPredicate());
			out.writeSerializedObject(event.getSelectionResult());
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			event = (InnerClusterExecutionEvent<R>) in.readObject();
			computationTokenCount = in.readInt();
			taskFactory = (TaskFactory<R>) in.readObject();
			taskContext = (TaskContext) in.readObject();
			taskUtilities = (TaskExecutionUtilities) in.readObject();
			duplicationPredicate = (TaskDuplicationPredicate) in.readObject();
			selectionResult = (SelectionResult) in.readObject();
		}

		@Override
		public Object resolveWrapped() {
			return this;
		}

		@Override
		public Object getWrappedObject() {
			return event;
		}

		@Override
		public boolean isActive() {
			return event.isActive();
		}

		@Override
		public void fail(Throwable cause) {
			//XXX async?
			event.fail(cause);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public void notifyResultReady(boolean lastresult) {
			//XXX async?
			event.notifyResultReady(lastresult);
		}

		@Override
		public void notifyNoMoreResults() {
			//XXX async?
			event.notifyNoMoreResults();
		}

		@Override
		public int getComputationTokenCount() {
			return computationTokenCount;
		}

		@Override
		public TaskFactory<R> getTaskFactory() {
			return taskFactory;
		}

		@Override
		public TaskContext getTaskContext() {
			return taskContext;
		}

		@Override
		public TaskExecutionUtilities getTaskUtilities() {
			return taskUtilities;
		}

		@Override
		public TaskDuplicationPredicate getDuplicationPredicate() {
			return duplicationPredicate;
		}

		@Override
		public void setInvocationHandle(InnerTaskInvocationHandle<R> resulthandle) {
			callRMIAsyncAssert(event, METHOD_SETINVOCATIONHANDLE, resulthandle);
//			event.setInvocationHandle(resulthandle);
		}

		@Override
		public void failInvocationStart(Exception e) {
			//XXX async?
			event.failInvocationStart(e);
		}

		@Override
		public SelectionResult getSelectionResult() {
			return selectionResult;
		}

		@Override
		public void failUnsuitable() {
			callRMIAsyncAssert(event, METHOD_FAILUNSUITABLE);
		}

		@Override
		public boolean notifyTaskInvocationStart() {
			return event.notifyTaskInvocationStart();
		}
	}

	@RMIWrap(InnerClusterExecutionEventRMIWrapper.class)
	private static class InnerClusterExecutionEventImpl<R>
			implements InnerClusterExecutionEvent<R>, InnerTaskInvocationHandle<R> {
		public static final int FLAG_HAD_RESPONSE = 1 << 1;
		public static final int FLAG_CANCEL_DUPLICATION = 1 << 2;
		public static final int FLAG_INTERRUPT = 1 << 3;

		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<TaskInvocationManager.InnerClusterExecutionEventImpl> AIFU_flags = AtomicIntegerFieldUpdater
				.newUpdater(TaskInvocationManager.InnerClusterExecutionEventImpl.class, "flags");
		private volatile int flags;

		private final TaskInvocationContext invocationContext;
		private final InnerTaskExecutionRequestImpl<R> request;
		private final ListenerInnerTaskInvocationHandler<R> invocationListener;
		private volatile InnerTaskInvocationHandle<R> handle;

		private final Object responseLock = new Object();

		public InnerClusterExecutionEventImpl(TaskInvocationContext invocationContext,
				InnerTaskExecutionRequestImpl<R> request, ListenerInnerTaskInvocationHandler<R> invocationListener) {
			this.invocationContext = invocationContext;
			this.request = request;
			this.invocationListener = invocationListener;
		}

		@Override
		public boolean isActive() {
			return request.isActive();
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public int getComputationTokenCount() {
			return request.getComputationTokenCount();
		}

		@Override
		public TaskFactory<R> getTaskFactory() {
			return request.getTaskFactory();
		}

		@Override
		public TaskContext getTaskContext() {
			return request.getTaskContext();
		}

		@Override
		public TaskExecutionUtilities getTaskUtilities() {
			return request.getTaskUtilities();
		}

		@Override
		public TaskDuplicationPredicate getDuplicationPredicate() {
			return request.getDuplicationPredicate();
		}

		@Override
		public SelectionResult getSelectionResult() {
			return request.getSelectionResult();
		}

		@Override
		public void setInvocationHandle(InnerTaskInvocationHandle<R> resulthandle) {
			request.duplicated();
			this.handle = resulthandle;

			int f = AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			synchronized (responseLock) {
				responseLock.notifyAll();
			}
			if (((f & FLAG_CANCEL_DUPLICATION) == FLAG_CANCEL_DUPLICATION)) {
				callRMIAsyncAssert(resulthandle, InnerTaskInvocationHandle.METHOD_CANCEL_DUPLICATION);
			}
			if (((f & FLAG_INTERRUPT) == FLAG_INTERRUPT)) {
				callRMIAsyncAssert(resulthandle, InnerTaskInvocationHandle.METHOD_INTERRUPT);
			}
			invocationListener.notifyStateChange();
		}

		@Override
		public void failInvocationStart(Exception e) {
			invocationListener.notifyNoMoreResults();
			request.failInit(invocationContext, e);
			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			synchronized (responseLock) {
				responseLock.notifyAll();
			}
		}

		@Override
		public void fail(Throwable cause) {
			invocationListener.hardFailed(cause);
			request.fail(invocationContext, cause);
			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			synchronized (responseLock) {
				responseLock.notifyAll();
			}
		}

		@Override
		public void failUnsuitable() {
			invocationListener.notifyNoMoreResults();
			//TODO unsuitability exception?
			request.fail(invocationContext, null);
			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			synchronized (responseLock) {
				responseLock.notifyAll();
			}
		}

		@Override
		public InnerTaskResultHolder<R> getResultIfPresent() throws InterruptedException {
			//wait the cluster response, to have the handle set
			waitForClusterResponse();
			InnerTaskInvocationHandle<R> h = handle;
			if (h == null) {
				//TODO this shouldnt happen, handle as error
				//the listener was notified about a new result, however the handle failed to set to this event
				//this is an error
				return null;
			}
			return h.getResultIfPresent();
		}

		@Override
		public void cancelDuplication() {
			InnerTaskInvocationHandle<R> h = handle;
			if (h == null) {
				AIFU_flags.updateAndGet(this, c -> c | FLAG_CANCEL_DUPLICATION);
				return;
			}
			callRMIAsyncAssert(h, InnerTaskInvocationHandle.METHOD_CANCEL_DUPLICATION);
		}

		@Override
		public void waitFinish() throws InterruptedException {
			// wait response from the cluster
			waitForClusterResponse();
			InnerTaskInvocationHandle<R> h = handle;
			if (h == null) {
				return;
			}
			h.waitFinish();
			return;
		}

		private void waitForClusterResponse() throws InterruptedException {
			if (((this.flags & FLAG_HAD_RESPONSE) != FLAG_HAD_RESPONSE)) {
				synchronized (responseLock) {
					while (true) {
						if (((this.flags & FLAG_HAD_RESPONSE) == FLAG_HAD_RESPONSE)) {
							break;
						}

						responseLock.wait();
					}
				}
			}
		}

		@Override
		public void interrupt() {
			InnerTaskInvocationHandle<R> h = handle;
			if (h == null) {
				AIFU_flags.updateAndGet(this, c -> c | FLAG_INTERRUPT);
				return;
			}
			callRMIAsyncAssert(h, InnerTaskInvocationHandle.METHOD_INTERRUPT);
		}

		@Override
		public void notifyResultReady(boolean lastresult) {
			invocationListener.notifyResultReady(lastresult);
		}

		@Override
		public void notifyNoMoreResults() {
			invocationListener.notifyNoMoreResults();
		}

		@Override
		public boolean notifyTaskInvocationStart() {
			return invocationListener.notifyTaskInvocationStart();
		}
	}

	private static void callRMIAsyncAssert(Object obj, Method m, Object... args) {
		try {
			RMIVariables.invokeRemoteMethodAsyncOrLocal(obj, m, args);
		} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
			throw new AssertionError(e);
		}
	}

	@RMIWrap(TaskClusterExecutionEventRMIWrapper.class)
	private static class TaskClusterExecutionEventImpl<R> implements ClusterExecutionEvent<R> {
		private final TaskInvocationContext invocationContext;
		private final TaskExecutionRequestImpl<R> request;

		public TaskClusterExecutionEventImpl(TaskInvocationContext invocationContext,
				TaskExecutionRequestImpl<R> request) {
			this.invocationContext = invocationContext;
			this.request = request;
		}

		@Override
		public boolean isActive() {
			return request.isActive();
		}

		@Override
		public void fail(Throwable cause) {
			request.fail(invocationContext, cause);
		}

		@Override
		public void failUnsuitable() {
			//TODO suitability failure exception
			request.fail(invocationContext, null);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public int getComputationTokenCount() {
			return request.getCapabilities().getComputationTokenCount();
		}

		@Override
		public TaskFactory<R> getTaskFactory() {
			return request.getFactory();
		}

		@Override
		public SelectionResult getSelectionResult() {
			return request.getSelectionResult();
		}

		@Override
		public TaskContext getTaskContext() {
			return request.getTaskContext();
		}

		@Override
		public boolean startExecution() {
			return request.startExecution(invocationContext);
		}

		@Override
		public void executionSuccessful(R taskresult) {
			request.executionSuccessful(taskresult);
		}

		@Override
		public void executionException(Throwable e) {
			request.executionException(e);
		}

		@Override
		public TaskExecutionUtilities getTaskUtilities() {
			return request.getTaskUtilities();
		}
	}

	@RMIWrap(TaskClusterExecutionEventRMIWrapper.class)
	private static class TaskClusterExecutionEventRMIWrapper<R> implements RMIWrapper, ClusterExecutionEvent<R> {
		private ClusterExecutionEvent<R> event;
		private int computationTokenCount;
		private SelectionResult selectionResult;
		private TaskContext taskContext;
		private TaskFactory<R> taskFactory;
		private TaskExecutionUtilities taskUtilities;

		@SuppressWarnings("unused")
		//used by the RMI runtime
		public TaskClusterExecutionEventRMIWrapper() {
		}

		@SuppressWarnings("unused")
		//used by the RMI runtime
		public TaskClusterExecutionEventRMIWrapper(ClusterExecutionEvent<R> event) {
			this.event = event;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(event);
			out.writeInt(event.getComputationTokenCount());
			out.writeSerializedObject(event.getSelectionResult());
			out.writeRemoteObject(event.getTaskContext());
			out.writeRemoteObject(event.getTaskUtilities());
			out.writeSerializedObject(event.getTaskFactory());
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			event = (ClusterExecutionEvent<R>) in.readObject();
			computationTokenCount = in.readInt();
			selectionResult = (SelectionResult) in.readObject();
			taskContext = (TaskContext) in.readObject();
			taskUtilities = (TaskExecutionUtilities) in.readObject();
			taskFactory = (TaskFactory<R>) in.readObject();
		}

		@Override
		public Object resolveWrapped() {
			return this;
		}

		@Override
		public Object getWrappedObject() {
			return event;
		}

		@Override
		public boolean isActive() {
			return event.isActive();
		}

		@Override
		public void fail(Throwable cause) {
			event.fail(cause);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public int getComputationTokenCount() {
			return computationTokenCount;
		}

		@Override
		public TaskFactory<R> getTaskFactory() {
			return taskFactory;
		}

		@Override
		public SelectionResult getSelectionResult() {
			return selectionResult;
		}

		@Override
		public TaskContext getTaskContext() {
			return taskContext;
		}

		@Override
		public TaskExecutionUtilities getTaskUtilities() {
			return taskUtilities;
		}

		@Override
		public boolean startExecution() {
			return event.startExecution();
		}

		@Override
		public void executionSuccessful(R taskresult) {
			event.executionSuccessful(taskresult);
		}

		@Override
		public void executionException(Throwable e) {
			event.executionException(e);
		}

		@Override
		public void failUnsuitable() {
			callRMIAsyncAssert(event, METHOD_FAILUNSUITABLE);
		}
	}

	private static class TaskInvocationContextImpl implements TaskInvocationContext {
		private List<TaskInvocationEvent> events = new ArrayList<>();
		private int pollEndIndex = 0;
		private final Object eventLock = new Object();
		private volatile boolean closed = false;
		private Throwable failException;
		private final UUID environmentIdentifier;

		public TaskInvocationContextImpl(UUID environmentIdentifier) {
			this.environmentIdentifier = environmentIdentifier;
		}

		public void run(TaskInvoker taskinvoker) throws Exception {
			try {
				taskinvoker.run(this);
				clusterExit();
			} catch (Throwable e) {
				clusterRunningAborted(e);
				throw e;
			}
		}

		public void clusterRunningAborted(Throwable e) {
			close(e);
		}

		public void clusterExit() {
			close(new IllegalStateException("Cluster closed."));
		}

		public UUID getEnvironmentIdentifier() {
			return environmentIdentifier;
		}

		public void close(Throwable e) {
			closed = true;
			synchronized (eventLock) {
				failException = IOUtils.addExc(failException, e);
				eventLock.notifyAll();
				for (TaskInvocationEvent ev : events) {
					ev.fail(e);
				}
				events.clear();
			}
		}

		public void addEvent(TaskInvocationEvent event) {
			event_add_block:
			synchronized (eventLock) {
				if (closed) {
					break event_add_block;
				}
				events.add(event);
				eventLock.notify();
				return;
			}
			event.fail(failException);
		}

		@Override
		public Iterable<TaskInvocationEvent> poll() throws InterruptedException {
			synchronized (eventLock) {
				while (!closed) {
//					if (events.isEmpty()) {
//						eventLock.wait();
//						continue;
//					}
					int eventcount = events.size();
					if (pollEndIndex >= eventcount) {
						eventLock.wait();
						continue;
					}

					List<TaskInvocationEvent> result = new ArrayList<>();
					do {
						TaskInvocationEvent ev = events.get(pollEndIndex++);
						if (!ev.isActive()) {
							continue;
						}
						result.add(ev);
					} while (pollEndIndex < eventcount);
//					for (Iterator<TaskInvocationEvent> it = result.iterator(); it.hasNext();) {
//						TaskInvocationEvent ev = it.next();
//						if (!ev.isActive()) {
//							it.remove();
//						}
//					}
					if (result.isEmpty()) {
						continue;
					}
					return result;
				}
			}
			return null;
		}
	}

	private static class InvokerSelectionFutureSupplier implements Supplier<SelectionResult> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskInvocationManager.InvokerSelectionFutureSupplier, Supplier> ARFU_result = AtomicReferenceFieldUpdater
				.newUpdater(TaskInvocationManager.InvokerSelectionFutureSupplier.class, Supplier.class, "result");

		private volatile Supplier<? extends SelectionResult> result;

		private Exception localSelectionException;

		public InvokerSelectionFutureSupplier(Exception localselectionexception) {
			this.localSelectionException = localselectionexception;
		}

		@Override
		public SelectionResult get() {
			Supplier<? extends SelectionResult> sr = result;
			if (sr != null) {
				return sr.get();
			}
			synchronized (this) {
				while (true) {
					sr = result;
					if (sr != null) {
						return sr.get();
					}
					try {
						this.wait();
					} catch (InterruptedException e) {
						IOUtils.addExc(e, localSelectionException);
						throw new TaskEnvironmentSelectionFailedException(e);
					}
				}
			}
		}

		public boolean hasResult() {
			return result != null;
		}

		public void fail() {
			setResult(() -> {
				throw new TaskEnvironmentSelectionFailedException(localSelectionException);
			});
		}

		public void setResult(Supplier<? extends SelectionResult> result) {
			if (ARFU_result.compareAndSet(this, null, result)) {
				synchronized (this) {
					this.notifyAll();
				}
			}
		}
	}

	private static class InvokerTaskResultsHandler<R> implements InnerTaskResults<R>, ManagerInnerTaskResults<R> {
		private ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationHandles;
		private final Object resultWaiterLock;
		private boolean duplicationCancellable;
		private volatile boolean allClustersFailed;

		public InvokerTaskResultsHandler(ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationHandles,
				Object resultwaiterlock, boolean duplicationCancellable) {
			this.invocationHandles = invocationHandles;
			this.resultWaiterLock = resultwaiterlock;
			this.duplicationCancellable = duplicationCancellable;
		}

		public void allClustersFailed() {
			allClustersFailed = true;
			synchronized (resultWaiterLock) {
				resultWaiterLock.notifyAll();
			}
		}

		@Override
		public InnerTaskResultHolder<R> getNext() throws InterruptedException {
			while (true) {
				if (allClustersFailed) {
					//XXX cause exceptions
					throw new InnerTaskInitializationException(
							"Failed to start inner task on the execution environments.");
				}
				{
					Iterator<ListenerInnerTaskInvocationHandler<R>> it = invocationHandles.iterator();
					if (!it.hasNext()) {
						return null;
					}
					do {
						ListenerInnerTaskInvocationHandler<R> ih = it.next();
						try {
							InnerTaskResultHolder<R> presentres = ih.getResultIfPresent();
							if (presentres != null) {
								return presentres;
							}
						} catch (Exception e) {
							//the handle should be removed, as we don't expect futher requests to succeed
							it.remove();
							return new FailedInnerTaskOptionalResult<>("Failed to retrieve inner task result.", e);
						}
					} while (it.hasNext());
				}
				ListenerInnerTaskInvocationHandler<R> availih = null;
				Iterator<ListenerInnerTaskInvocationHandler<R>> availihit = invocationHandles.iterator();
				if (!availihit.hasNext()) {
					return null;
				}
				synchronized (resultWaiterLock) {
					boolean hadhandler = false;
					do {
						ListenerInnerTaskInvocationHandler<R> ih = availihit.next();
						if (ih.isEnded()) {
							if (!ih.isResultAvailable()) {
								availihit.remove();
								continue;
							}
							availih = ih;
							break;
						}
						if (ih.isResultAvailable()) {
							availih = ih;
							break;
						}
						hadhandler = true;
					} while (availihit.hasNext());
					if (availih == null) {
						if (!hadhandler) {
							//no more handlers found
							return null;
						}
						//clear the iterator so it is garbage collectable while waiting
						availihit = null;
						resultWaiterLock.wait();
						continue;
					}
				}
				try {
					InnerTaskResultHolder<R> availpresent = availih.getResultIfPresent();
					if (availpresent != null) {
						return availpresent;
					}
				} catch (Exception e) {
					//the handle should be removed, as we don't expect futher requests to succeed
					availihit.remove();
					return new FailedInnerTaskOptionalResult<>("Failed to retrieve inner task result.", e);
				}
				//continue loop
			}
		}

		@Override
		public void cancelDuplicationOptionally() {
			if (!duplicationCancellable) {
				return;
			}
			for (Iterator<ListenerInnerTaskInvocationHandler<R>> it = invocationHandles.iterator(); it.hasNext();) {
				ListenerInnerTaskInvocationHandler<R> ih = it.next();
				//exceptions in the following shouldn't happen, as the associated method is called asynchronously
				ih.cancelDuplicationOptionally();
			}
		}

		@Override
		public void waitFinishCancelOptionally() throws InterruptedException, RMIRuntimeException {
			if (duplicationCancellable) {
				for (Iterator<ListenerInnerTaskInvocationHandler<R>> it = invocationHandles.iterator(); it.hasNext();) {
					ListenerInnerTaskInvocationHandler<R> ih = it.next();
					//exceptions in the following shouldn't happen, as the associated method is called asynchronously
					ih.cancelDuplicationOptionally();
				}
			}
			for (ListenerInnerTaskInvocationHandler<R> ih : invocationHandles) {
				try {
					ih.waitFinish();
				} catch (InterruptedException e) {
					throw e;
				}
			}
		}

		@Override
		public void interrupt() {
			for (ListenerInnerTaskInvocationHandler<R> ih : invocationHandles) {
				//exceptions in the following shouldn't happen, as the associated method is called asynchronously
				ih.interrupt();
			}
		}
	}

	private static class TaskResultReadyCountState {
		public static final TaskResultReadyCountState ZERO = new TaskResultReadyCountState(0, 0);

		protected final int readyCount;
		protected final int invokingCount;
		/**
		 * <code>null</code> if there was no hard failure yet. <br>
		 * Non-empty array if there are unretrieved exceptions. <br>
		 * Empty array if we had a hard failure, but the exceptions was relayed to the client. <br>
		 */
		protected final Throwable[] hardFail;

		public TaskResultReadyCountState(int readyCount, int invokingCount) {
			this.readyCount = readyCount;
			this.invokingCount = invokingCount;
			this.hardFail = null;
		}

		public TaskResultReadyCountState(int readyCount, int invokingCount, Throwable[] hardFail) {
			this.readyCount = readyCount;
			this.invokingCount = invokingCount;
			this.hardFail = hardFail;
		}

		public TaskResultReadyCountState addReady() {
			return new TaskResultReadyCountState(readyCount + 1, invokingCount);
		}

		public TaskResultReadyCountState addInvoking() {
			return new TaskResultReadyCountState(readyCount, invokingCount + 1);
		}
	}

	private static class ListenerInnerTaskInvocationHandler<R> implements InnerTaskInvocationListener {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskInvocationManager.ListenerInnerTaskInvocationHandler, TaskResultReadyCountState> ARFU_readyState = AtomicReferenceFieldUpdater
				.newUpdater(TaskInvocationManager.ListenerInnerTaskInvocationHandler.class,
						TaskResultReadyCountState.class, "readyState");
		private volatile TaskResultReadyCountState readyState = TaskResultReadyCountState.ZERO;

		protected InnerTaskInvocationHandle<R> handle;
		private volatile boolean ended;

		private final Object resultWaiterLock;
		private final TaskContext taskContext;

		public ListenerInnerTaskInvocationHandler(Object resultWaiterLock, TaskContext taskcontext) {
			this.resultWaiterLock = resultWaiterLock;
			this.taskContext = taskcontext;
		}

		public boolean isEnded() {
			return ended;
		}

		public void cancelDuplicationOptionally() {
			if (ended) {
				return;
			}
			handle.cancelDuplication();
		}

		public void waitFinish() throws InterruptedException {
			if (ended) {
				return;
			}
			handle.waitFinish();
		}

		public void interrupt() {
			if (ended) {
				return;
			}
			handle.interrupt();
		}

		public boolean isResultAvailable() {
			TaskResultReadyCountState s = readyState;
			return s.readyCount > 0 || s.hardFail != null;
		}

		public InnerTaskResultHolder<R> getResultIfPresent() throws Exception {
			while (true) {
				TaskResultReadyCountState s = this.readyState;
				if (s.readyCount > 0) {
					if (!ARFU_readyState.compareAndSet(this, s,
							new TaskResultReadyCountState(s.readyCount - 1, s.invokingCount))) {
						continue;
					}
					return handle.getResultIfPresent();
				}
				if (!ObjectUtils.isNullOrEmpty(s.hardFail)) {
					if (!ARFU_readyState.compareAndSet(this, s,
							new TaskResultReadyCountState(s.readyCount, s.invokingCount, null))) {
						continue;
					}
					Throwable firsthardfailexc = s.hardFail[0];
					for (int i = 1; i < s.hardFail.length; i++) {
						firsthardfailexc.addSuppressed(s.hardFail[1]);
					}
					return new FailedInnerTaskOptionalResult<>(firsthardfailexc);
				}
				//no results ready
				return null;
			}
		}

		@Override
		public void notifyResultReady(boolean lastresult) {
			ARFU_readyState.updateAndGet(this, TaskResultReadyCountState::addReady);
			synchronized (resultWaiterLock) {
				if (lastresult) {
					//set the ended flag in the lock
					ended = true;
					resultWaiterLock.notifyAll();
				} else {
					resultWaiterLock.notify();
				}
			}
		}

		@Override
		public void notifyNoMoreResults() {
			synchronized (resultWaiterLock) {
				ended = true;
				resultWaiterLock.notifyAll();
			}
		}

		public void notifyStateChange() {
			synchronized (resultWaiterLock) {
				resultWaiterLock.notifyAll();
			}
		}

		@Override
		public boolean notifyTaskInvocationStart() {
			if (ended) {
				return false;
			}
			while (true) {
				TaskResultReadyCountState s = this.readyState;
				if (s.hardFail != null) {
					//we had a hard failure, no more invocations
					return false;
				}
				if (ARFU_readyState.compareAndSet(this, s, s.addInvoking())) {
					return true;
				}
			}
		}

		public void hardFailed(Throwable cause) {
			while (true) {
				TaskResultReadyCountState s = readyState;
				if (s.invokingCount == 0) {
					//no need to return a result with an exception, as there are no currently invoking tasks
					taskContext.getTaskUtilities().reportIgnoredException(cause);
					break;
				}
				if (s.hardFail == null) {
					if (ARFU_readyState.compareAndSet(this, s,
							new TaskResultReadyCountState(s.readyCount, s.invokingCount, new Throwable[] { cause }))) {
						break;
					}
					continue;
				}
				if (ARFU_readyState.compareAndSet(this, s, new TaskResultReadyCountState(s.readyCount, s.invokingCount,
						ArrayUtils.appended(s.hardFail, cause)))) {
					break;
				}
				continue;
			}
			synchronized (resultWaiterLock) {
				this.ended = true;
				resultWaiterLock.notifyAll();
			}
		}

		public TaskResultReadyCountState getReadyState() {
			return readyState;
		}
	}
}
