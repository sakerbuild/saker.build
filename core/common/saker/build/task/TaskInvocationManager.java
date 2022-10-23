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
import java.util.Collections;
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
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListWrapper;
import saker.build.thirdparty.saker.util.thread.BooleanLatch;
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
		@Deprecated
		public InnerTaskResultHolder<R> getResultIfPresent() throws InterruptedException;

		@RMIWrap(MultiInnerTaskResultHolderRMIWrapper.class)
		public default Iterable<? extends InnerTaskResultHolder<R>> getResultsIfAny() throws InterruptedException {
			InnerTaskResultHolder<R> r = getResultIfPresent();
			if (r != null) {
				return Collections.singleton(r);
			}
			return null;
		}

		public void waitFinish() throws InterruptedException;

		public void cancelDuplication();

		public void interrupt();
	}

	public interface InnerTaskInvocationListener {
		public static final Method METHOD_NOTIFYRESULTREADY = ReflectUtils
				.getMethodAssert(InnerTaskInvocationListener.class, "notifyResultReady", boolean.class);
		public static final Method METHOD_NOTIFYNOMORERESULTS = ReflectUtils
				.getMethodAssert(InnerTaskInvocationListener.class, "notifyNoMoreResults");

		public void notifyResultReady(boolean lastresult);

		public void notifyNoMoreResults();

		public InnerTaskInstanceInvocationHandle notifyTaskInvocationStart();
	}

	public interface InnerTaskInstanceInvocationHandle {
		public static final Method METHOD_DONE = ReflectUtils.getMethodAssert(InnerTaskInstanceInvocationHandle.class,
				"done");
		public static final Method METHOD_DONENOMORERESULTS = ReflectUtils
				.getMethodAssert(InnerTaskInstanceInvocationHandle.class, "doneNoMoreResults");

		public void done();

		public void doneNoMoreResults();
	}

	private SakerEnvironmentImpl environment;
	private ExecutionContextImpl executionContext;
	private SakerEnvironment localTesterEnvironment;

	private ThreadUtils.ThreadWorkPool invokerPool;
	private LazySupplier<?> clusterStartSupplier;
	private Collection<TaskInvocationContextImpl> invocationContexts;

	private volatile boolean closed = false;
	private Collection<? extends TaskInvokerFactory> invokerFactories;

	private InnerTaskInvocationManager innerTaskInvoker;

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

		this.innerTaskInvoker = new InnerTaskInvocationManager(executionContext);
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
		TaskExecutionEnvironmentSelector environmentselector = configuration.getExecutionEnvironmentSelector();
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
		if (remotedispatchable) {
			if (environmentselector.isRestrictedToLocalEnvironment()) {
				remotedispatchable = false;
			}
		}

		if (!remotedispatchable || ObjectUtils.isNullOrEmpty(invokerFactories)) {
			final Exception finallocalselectionexception = localselectionexception;
			return () -> {
				if (finallocalselectionexception != null) {
					throw new TaskEnvironmentSelectionFailedException(finallocalselectionexception);
				}
				throw new TaskEnvironmentSelectionFailedException("No suitable build environment found.");
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

	public <R> TaskInvocationResult<R> invokeTaskRunning(TaskFactory<R> factory,
			TaskInvocationConfiguration capabilities, SelectionResult selectionresult,
			TaskExecutorContext<R> taskcontext) throws InterruptedException, ClusterTaskExecutionFailedException {
		//TODO if the given number of tokens are available from the start, try request it and run on the local
		//     machine if available
		boolean preferslocalenv = capabilities.isPrefersLocalEnvironment();
		boolean localhasdiffs = true;
		if (preferslocalenv) {
			localhasdiffs = hasAnyEnvironmentPropertyDifferenceWithLocalEnvironment(selectionresult);
			if (!localhasdiffs) {
				return invokeTaskRunningOnLocalEnvironment(factory, capabilities, taskcontext);
			}
			//proceed with cluster invocation
		}
		if (capabilities.isRemoteDispatchable() && !ObjectUtils.isNullOrEmpty(invokerFactories)) {
			TaskInvocationResult<R> invocationresult = invokeTaskRunningOnClustersAndWaitForResult(factory,
					capabilities, selectionresult, taskcontext);
			if (invocationresult != null) {
				return invocationresult;
			}
			//all clusters failed to invoke
			//proceed with invoking on the local if possible
		}
		if (!preferslocalenv) {
			//don't check twice. if local env is preferred, then we already checked differences 
			localhasdiffs = hasAnyEnvironmentPropertyDifferenceWithLocalEnvironment(selectionresult);
		}
		if (localhasdiffs) {
			//either at least one of the cluster is suitable
			//or the local is
			//if we reach here, either the suitable cluster hasn't responded
			//or the local environment is not suitable
			throw new ClusterTaskExecutionFailedException("Suitable execution environment is no longer accessible.");
		}
		return invokeTaskRunningOnLocalEnvironment(factory, capabilities, taskcontext);
	}

	private boolean hasAnyEnvironmentPropertyDifferenceWithLocalEnvironment(SelectionResult selectionresult) {
		return SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(localTesterEnvironment,
				selectionresult.getQualifierEnvironmentProperties());
	}

	//suppress unused TaskContextReference
	@SuppressWarnings("try")
	private <R> TaskInvocationResult<R> invokeTaskRunningOnLocalEnvironment(TaskFactory<R> factory,
			TaskInvocationConfiguration capabilities, TaskExecutorContext<R> taskcontext) throws InterruptedException {
		int tokencount = capabilities.getRequestedComputationTokenCount();
		try (ComputationToken ctoken = ComputationToken.request(taskcontext, tokencount)) {
			Task<? extends R> task;
			try {
				task = factory.createTask(executionContext);
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				return new TaskInvocationResult<>(null, e);
			}
			if (task == null) {
				return new TaskInvocationResult<>(null, new NullPointerException(
						"TaskFactory " + factory.getClass().getName() + " created null Task."));
			}
			R taskres;
			InternalTaskBuildTrace btrace = taskcontext.internalGetBuildTrace();
			try (TaskContextReference contextref = TaskContextReference.createForMainTask(taskcontext, btrace)) {
				btrace.startTaskExecution();
				try {
					taskres = task.run(taskcontext);
				} finally {
					btrace.endTaskExecution();
				}
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				return new TaskInvocationResult<>(null, e);
			} finally {
				ctoken.closeAll();
			}
			return new TaskInvocationResult<>(Optional.ofNullable(taskres), null);
		}
	}

	private <R> TaskInvocationResult<R> invokeTaskRunningOnClustersAndWaitForResult(TaskFactory<R> factory,
			TaskInvocationConfiguration capabilities, SelectionResult selectionresult,
			TaskExecutorContext<R> taskcontext) throws InterruptedException {
		ensureClustersStarted();

		TaskExecutionRequestImpl<R> request = new TaskExecutionRequestImpl<>(invocationContexts, factory, capabilities,
				selectionresult, taskcontext);
		for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
			invocationcontext.addEvent(new TaskClusterExecutionEventImpl<>(invocationcontext, request));
		}
		//XXX we shouldn't wait for all clusters to respond, as if we can invoke the task locally, 
		//    it will be bottlenecked by the slowest cluster
		request.waitForResult();
		TaskInvocationResult<R> invocationresult = request.toTaskInvocationResult();
		return invocationresult;
	}

	public <R> ManagerInnerTaskResults<R> invokeInnerTaskRunning(TaskFactory<R> taskfactory,
			SelectionResult selectionresult, int duplicationfactor, boolean duplicationcancellable,
			TaskExecutorContext<?> taskcontext, TaskDuplicationPredicate duplicationPredicate,
			TaskInvocationConfiguration configuration, Set<UUID> allowedenvironmentids, int maximumenvironmentfactor) {
		ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationhandles = new ConcurrentLinkedQueue<>();
		final Object resultwaiterlock = new Object();
		Exception invokerexceptions = null;
		int computationtokencount = configuration.getRequestedComputationTokenCount();
		boolean postedtolocalenv = false;

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
				duplicationcancellable, taskcontext);

		if (posttoenvironmentpredicate.test(environment.getEnvironmentIdentifier())) {
			boolean hasdiffs = hasAnyEnvironmentPropertyDifferenceWithLocalEnvironment(selectionresult);
			if (!hasdiffs) {
				ListenerInnerTaskInvocationHandler<R> listener = new ListenerInnerTaskInvocationHandler<>(
						resultwaiterlock, taskcontext);
				try {
					InnerTaskInvocationHandle<R> handle = innerTaskInvoker.invokeInnerTask(taskfactory, taskcontext,
							listener, taskcontext, computationtokencount, duplicationPredicate,
							maximumenvironmentfactor);
					listener.handle = handle;
					postedtolocalenv = true;
					invocationhandles.add(listener);
					fut.setLocallyRunnable(true);
				} catch (Exception e) {
					invokerexceptions = IOUtils.addExc(invokerexceptions, e);
				}
			}
		}

		if (configuration.isRemoteDispatchable() && !(postedtolocalenv && configuration.isPrefersLocalEnvironment())) {
			//don't post to build clusters if prefers the local environment and is being run on it
			ensureClustersStarted();
			InnerTaskExecutionRequestImpl<R> request = new InnerTaskExecutionRequestImpl<>(invocationContexts, fut,
					computationtokencount, taskfactory, taskcontext, duplicationPredicate, selectionresult,
					duplicationfactor, maximumenvironmentfactor);
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

		public void close(@RMISerialize Throwable cause);

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

		@RMICacheResult
		public int getMaximumEnvironmentFactor();

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

		public void failInvocationStart(@RMISerialize Throwable e);

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

		public abstract void close(TaskInvocationContext invocationcontext, Throwable cause);

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

		@Override
		public void close(TaskInvocationContext invocationcontext, Throwable cause) {
			super.fail(invocationcontext, cause);
		}

	}

	private static class InnerTaskExecutionRequestImpl<R> extends BaseInvocationRequest {
		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<TaskInvocationManager.InnerTaskExecutionRequestImpl> AIFU_duplicationCount = AtomicIntegerFieldUpdater
				.newUpdater(TaskInvocationManager.InnerTaskExecutionRequestImpl.class, "duplicationCount");
		private volatile int duplicationCount;

		private final InvokerTaskResultsHandler<R> resultsHandler;
		private final int computationTokenCount;
		private final int maximumEnvironmentFactor;
		private final TaskFactory<R> taskFactory;
		private final TaskContext taskContext;
		private final TaskExecutionUtilities taskUtilities;
		private final TaskDuplicationPredicate duplicationPredicate;
		private final SelectionResult selectionResult;

		public InnerTaskExecutionRequestImpl(Collection<? extends TaskInvocationContext> invocationcontexts,
				InvokerTaskResultsHandler<R> resultsHandler, int computationTokenCount, TaskFactory<R> taskFactory,
				TaskContext taskContext, TaskDuplicationPredicate duplicationPredicate, SelectionResult selectionResult,
				int duplicationfactor, int maximumenvironmentfactor) {
			super(invocationcontexts);
			this.resultsHandler = resultsHandler;
			this.computationTokenCount = computationTokenCount;
			this.taskFactory = taskFactory;
			this.taskContext = taskContext;
			this.duplicationPredicate = duplicationPredicate;
			this.selectionResult = selectionResult;
			this.duplicationCount = duplicationfactor == 0 ? 1 : duplicationfactor;
			this.maximumEnvironmentFactor = maximumenvironmentfactor;

			this.taskUtilities = taskContext.getTaskUtilities();
		}

		public void failInit(TaskInvocationContext invocationcontext, Throwable e) {
			super.fail(invocationcontext, e);
		}

		@Override
		public void close(TaskInvocationContext invocationcontext, Throwable cause) {
			super.fail(invocationcontext, cause);
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

		public int getMaximumEnvironmentFactor() {
			return maximumEnvironmentFactor;
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
		private final BooleanLatch finishedLatch = BooleanLatch.newBooleanLatch();

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
			finishedLatch.signal();
		}

		public void executionSuccessful(R taskresult) {
			result = taskresult;
			finishedLatch.signal();
		}

		public void executionException(Throwable e) {
			resultException = e;
			finishedLatch.signal();
		}

		@Override
		public void fail(TaskInvocationContext invocationcontext, Throwable cause) {
			if (isStartedExecution(invocationcontext)) {
				failWithStartedExecution(invocationcontext, cause);
			} else {
				super.fail(invocationcontext, cause);
			}
		}

		@Override
		public void close(TaskInvocationContext invocationcontext, Throwable cause) {
			if (finishedLatch.isSignalled()) {
				//already finished, closing is okay
				return;
			}
			resultException = new IllegalStateException("Task execution request closed.", cause);
			finishedLatch.signal();
			super.fail(invocationcontext, cause);
		}

		private void failWithStartedExecution(TaskInvocationContext invocationContext, Throwable cause) {
			resultException = new ClusterTaskExecutionFailedException(cause);
			finishedLatch.signal();
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
			finishedLatch.await();
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
		public void close(Throwable cause) {
			event.close(cause);
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
		public void close(Throwable cause) {
			request.close(invocationContext, cause);
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
		private int maximumEnvironmentFactor;

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
			out.writeInt(event.getMaximumEnvironmentFactor());
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
			maximumEnvironmentFactor = in.readInt();
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
		public void close(Throwable cause) {
			//XXX async?
			event.close(cause);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public void notifyResultReady(boolean lastresult) {
			callRMIAsyncAssert(event, METHOD_NOTIFYRESULTREADY, lastresult);
		}

		@Override
		public void notifyNoMoreResults() {
			callRMIAsyncAssert(event, METHOD_NOTIFYNOMORERESULTS);
		}

		@Override
		public int getComputationTokenCount() {
			return computationTokenCount;
		}

		@Override
		public int getMaximumEnvironmentFactor() {
			return maximumEnvironmentFactor;
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
		public void failInvocationStart(Throwable e) {
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
		public InnerTaskInstanceInvocationHandle notifyTaskInvocationStart() {
			return event.notifyTaskInvocationStart();
		}
	}

	@RMIWrap(InnerClusterExecutionEventRMIWrapper.class)
	private static class InnerClusterExecutionEventImpl<R>
			implements InnerClusterExecutionEvent<R>, InnerTaskInvocationHandle<R> {
		public static final int FLAG_HAD_RESPONSE = 1 << 1;
		public static final int FLAG_CANCEL_DUPLICATION = 1 << 2;
		public static final int FLAG_INTERRUPT = 1 << 3;
		public static final int FLAG_CLOSED = 1 << 4;

		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<TaskInvocationManager.InnerClusterExecutionEventImpl> AIFU_flags = AtomicIntegerFieldUpdater
				.newUpdater(TaskInvocationManager.InnerClusterExecutionEventImpl.class, "flags");
		private volatile int flags;

		private final TaskInvocationContext invocationContext;
		private final InnerTaskExecutionRequestImpl<R> request;
		private final ListenerInnerTaskInvocationHandler<R> invocationListener;
		private volatile InnerTaskInvocationHandle<R> handle;

		private final BooleanLatch responseLatch = BooleanLatch.newBooleanLatch();

		private final ConcurrentPrependAccumulator<InnerTaskResultHolder<R>> presentResults = new ConcurrentPrependAccumulator<>();

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
		public int getMaximumEnvironmentFactor() {
			return request.getMaximumEnvironmentFactor();
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
			responseLatch.signal();
			if (((f & FLAG_CANCEL_DUPLICATION) == FLAG_CANCEL_DUPLICATION)) {
				callRMIAsyncAssert(resulthandle, InnerTaskInvocationHandle.METHOD_CANCEL_DUPLICATION);
			}
			if (((f & FLAG_INTERRUPT) == FLAG_INTERRUPT)) {
				callRMIAsyncAssert(resulthandle, InnerTaskInvocationHandle.METHOD_INTERRUPT);
			}
			invocationListener.notifyStateChange();
		}

		@Override
		public void failInvocationStart(Throwable e) {
			invocationListener.notifyNoMoreResults();
			request.failInit(invocationContext, e);
			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			responseLatch.signal();
		}

		@Override
		public void fail(Throwable cause) {
			invocationListener.hardFailed(cause);
			request.fail(invocationContext, cause);
			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			responseLatch.signal();
		}

		@Override
		public void close(Throwable cause) {
			invocationListener.closed(cause);
			request.close(invocationContext, cause);
			AIFU_flags.updateAndGet(this, c -> c | (FLAG_HAD_RESPONSE | FLAG_CLOSED | FLAG_CANCEL_DUPLICATION));
			responseLatch.signal();
		}

		@Override
		public void failUnsuitable() {
			invocationListener.notifyNoMoreResults();
			//TODO unsuitability exception?
			request.fail(invocationContext, null);
			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			responseLatch.signal();
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
			InnerTaskResultHolder<R> present = presentResults.take();
			if (present != null) {
				return present;
			}
			Iterable<? extends InnerTaskResultHolder<R>> results = h.getResultsIfAny();
			if (results == null) {
				return null;
			}
			Iterator<? extends InnerTaskResultHolder<R>> it = results.iterator();
			if (!it.hasNext()) {
				return null;
			}
			InnerTaskResultHolder<R> methodresult = it.next();
			it.forEachRemaining(presentResults::add);
			return methodresult;
		}

		@Override
		public Iterable<? extends InnerTaskResultHolder<R>> getResultsIfAny() throws InterruptedException {
			//wait the cluster response, to have the handle set
			waitForClusterResponse();
			InnerTaskInvocationHandle<R> h = handle;
			if (h == null) {
				//TODO this shouldnt happen, handle as error
				//the listener was notified about a new result, however the handle failed to set to this event
				//this is an error
				return null;
			}
			if (!presentResults.isEmpty()) {
				return presentResults.clearAndIterable();
			}
			return h.getResultsIfAny();
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
			responseLatch.await();
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
		public InnerTaskInstanceInvocationHandle notifyTaskInvocationStart() {
			if (((this.flags & FLAG_CLOSED) == FLAG_CLOSED)) {
				return null;
			}
			return invocationListener.notifyTaskInvocationStart();
		}
	}

	protected static void callRMIAsyncAssert(Object obj, Method m, Object... args) {
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
		public void close(Throwable cause) {
			request.close(invocationContext, cause);
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
			return request.getCapabilities().getRequestedComputationTokenCount();
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
		public void close(Throwable cause) {
			event.close(cause);
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
		private Throwable closeException;
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
			List<TaskInvocationEvent> evlist;
			IllegalStateException failexc;
			synchronized (eventLock) {
				evlist = this.events;

				closed = true;
				closeException = IOUtils.addExc(closeException, e);
				events = Collections.emptyList();
				pollEndIndex = 0;

				eventLock.notifyAll();

				failexc = createClosedException(closeException);
			}
			for (TaskInvocationEvent ev : evlist) {
				ev.close(failexc);
			}
		}

		private static IllegalStateException createClosedException(Throwable cexc) {
			return new IllegalStateException("Task invocation context closed.", cexc);
		}

		public void addEvent(TaskInvocationEvent event) {
			Throwable closeex;
			event_add_block:
			synchronized (eventLock) {
				closeex = closeException;
				if (closed) {
					break event_add_block;
				}
				events.add(event);
				eventLock.notify();
				return;
			}
			event.close(createClosedException(closeex));
		}

		@Override
		public Iterable<TaskInvocationEvent> poll() throws InterruptedException {
			synchronized (eventLock) {
				while (!closed) {
					List<TaskInvocationEvent> evlist = events;
					int eventcount = evlist.size();
					if (pollEndIndex >= eventcount) {
						eventLock.wait();
						continue;
					}

					List<TaskInvocationEvent> result = new ArrayList<>();
					do {
						TaskInvocationEvent ev = evlist.get(pollEndIndex++);
						if (!ev.isActive()) {
							continue;
						}
						result.add(ev);
					} while (pollEndIndex < eventcount);
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
			synchronized (InvokerSelectionFutureSupplier.this) {
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
				synchronized (InvokerSelectionFutureSupplier.this) {
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
		private boolean locallyRunnable;
		private TaskExecutorContext<?> taskContext;

		public InvokerTaskResultsHandler(ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationHandles,
				Object resultwaiterlock, boolean duplicationCancellable, TaskExecutorContext<?> taskcontext) {
			this.invocationHandles = invocationHandles;
			this.resultWaiterLock = resultwaiterlock;
			this.duplicationCancellable = duplicationCancellable;
			this.taskContext = taskcontext;
		}

		public void setLocallyRunnable(boolean locallyRunnable) {
			this.locallyRunnable = locallyRunnable;
		}

		public void allClustersFailed() {
			allClustersFailed = true;
			synchronized (resultWaiterLock) {
				resultWaiterLock.notifyAll();
			}
		}

		@Override
		public InnerTaskResultHolder<R> getNext() throws InterruptedException {
			taskContext.requireCalledOnMainThread(true);
			return internalGetNextOnTaskThread();
		}

		@Override
		public InnerTaskResultHolder<R> internalGetNextOnTaskThread() throws InterruptedException {
			return internalGetNextImpl();
		}

		@Override
		public InnerTaskResultHolder<R> internalGetNextOnExecutionFinish() throws InterruptedException {
			return internalGetNextImpl();
		}

		private InnerTaskResultHolder<R> internalGetNextImpl() throws InterruptedException {
			while (true) {
				if (!locallyRunnable && allClustersFailed) {
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
								if (!ih.isAnyMoreResultsExpected()) {
									availihit.remove();
								} else {
									hadhandler = true;
								}
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
				ih.waitFinish();
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
		public static final TaskResultReadyCountState ZERO = new TaskResultReadyCountState(0, 0, 0);

		protected final int readyCount;
		protected final int notifiedCount;
		protected final int invokingCount;
		/**
		 * <code>null</code> if there was no hard failure yet. <br>
		 * Non-empty array if there are unretrieved exceptions. <br>
		 * Empty array if we had a hard failure, but the exceptions was relayed to the client. <br>
		 */
		protected final Throwable[] hardFail;

		public TaskResultReadyCountState(int readyCount, int invokingCount, int notifiedCount) {
			this.readyCount = readyCount;
			this.invokingCount = invokingCount;
			this.notifiedCount = notifiedCount;
			this.hardFail = null;
		}

		public TaskResultReadyCountState(int readyCount, int invokingCount, int notifiedCount, Throwable[] hardFail) {
			this.readyCount = readyCount;
			this.invokingCount = invokingCount;
			this.notifiedCount = notifiedCount;
			this.hardFail = hardFail;
		}

		public TaskResultReadyCountState addReady() {
			return new TaskResultReadyCountState(readyCount + 1, invokingCount, notifiedCount + 1);
		}

		public TaskResultReadyCountState addInvoking() {
			return new TaskResultReadyCountState(readyCount, invokingCount + 1, notifiedCount);
		}
	}

	@RMIWrap(InnerTaskInstanceInvocationHandleRMIWrapper.class)
	private static final class InnerTaskInstanceInvocationHandleImpl implements InnerTaskInstanceInvocationHandle {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskInvocationManager.InnerTaskInstanceInvocationHandleImpl, ListenerInnerTaskInvocationHandler> ARFU_handler = AtomicReferenceFieldUpdater
				.newUpdater(TaskInvocationManager.InnerTaskInstanceInvocationHandleImpl.class,
						ListenerInnerTaskInvocationHandler.class, "handler");

		@SuppressWarnings("unused")
		private volatile ListenerInnerTaskInvocationHandler<?> handler;

		public InnerTaskInstanceInvocationHandleImpl(ListenerInnerTaskInvocationHandler<?> handler) {
			this.handler = handler;
		}

		@Override
		public void done() {
			ListenerInnerTaskInvocationHandler<?> handler = ARFU_handler.getAndSet(this, null);
			if (handler != null) {
				handler.oneDone(this);
			}
		}

		@Override
		public void doneNoMoreResults() {
			ListenerInnerTaskInvocationHandler<?> handler = ARFU_handler.getAndSet(this, null);
			if (handler != null) {
				handler.oneDoneNoMoreResults(this);
			}
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
		private final TaskExecutorContext<?> taskContext;

		private final Set<InnerTaskInstanceInvocationHandleImpl> startedTaskInvocations = ConcurrentHashMap.newKeySet();

		public ListenerInnerTaskInvocationHandler(Object resultWaiterLock, TaskExecutorContext<?> taskcontext) {
			this.resultWaiterLock = resultWaiterLock;
			this.taskContext = taskcontext;
		}

		public void oneDone(InnerTaskInstanceInvocationHandleImpl handle) {
//			taskContext.executionManager.removeRunningThreadCount(1);
			startedTaskInvocations.remove(handle);
			notifyResultReadyImpl(false);
		}

		public void oneDoneNoMoreResults(InnerTaskInstanceInvocationHandleImpl handle) {
//			taskContext.executionManager.removeRunningThreadCount(1);
			startedTaskInvocations.remove(handle);
			notifyResultReadyImpl(true);
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

		public boolean isAnyMoreResultsExpected() {
			TaskResultReadyCountState s = readyState;
			return s.notifiedCount < s.invokingCount || !startedTaskInvocations.isEmpty();
		}

		public InnerTaskResultHolder<R> getResultIfPresent() throws Exception {
			while (true) {
				TaskResultReadyCountState s = this.readyState;
				if (s.readyCount > 0) {
					if (!ARFU_readyState.compareAndSet(this, s,
							new TaskResultReadyCountState(s.readyCount - 1, s.invokingCount, s.notifiedCount))) {
						continue;
					}
					InnerTaskResultHolder<R> res = handle.getResultIfPresent();
					if (res == null) {
						throw new AssertionError("Failed to retrieve inner task result.");
					}
					return res;
				}
				if (!ObjectUtils.isNullOrEmpty(s.hardFail)) {
					if (!ARFU_readyState.compareAndSet(this, s,
							new TaskResultReadyCountState(s.readyCount, s.invokingCount, s.notifiedCount, null))) {
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
			notifyResultReadyImpl(lastresult);
		}

		private void notifyResultReadyImpl(boolean lastresult) {
			ARFU_readyState.updateAndGet(this, TaskResultReadyCountState::addReady);
			synchronized (resultWaiterLock) {
				if (lastresult) {
					//set the ended flag in the lock
					ended = true;
					resultWaiterLock.notifyAll();
				} else {
					if (ended) {
						//there might be scenarios when the "no more result" notification arrives before the 
						// "result ready" notification. in this case we need to notify all
						resultWaiterLock.notifyAll();
					} else {
						resultWaiterLock.notify();
					}
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
		public InnerTaskInstanceInvocationHandle notifyTaskInvocationStart() {
			if (ended) {
				return null;
			}
			while (true) {
				TaskResultReadyCountState s = this.readyState;
				if (s.hardFail != null) {
					//we had a hard failure, no more invocations
					return null;
				}
				if (ARFU_readyState.compareAndSet(this, s, s.addInvoking())) {
					InnerTaskInstanceInvocationHandleImpl result = new InnerTaskInstanceInvocationHandleImpl(this);
					startedTaskInvocations.add(result);
//					taskContext.executionManager.addRunningThreadCount(1);
					return result;
				}
			}
		}

		public void closed(Throwable cause) {
			TaskResultReadyCountState s = readyState;
			if (s.invokingCount == 0) {
				//its okay to close
				ended = true;
				return;
			}
			hardFailed(cause);
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
					if (ARFU_readyState.compareAndSet(this, s, new TaskResultReadyCountState(s.readyCount,
							s.invokingCount, s.notifiedCount, new Throwable[] { cause }))) {
						break;
					}
					continue;
				}
				if (ARFU_readyState.compareAndSet(this, s, new TaskResultReadyCountState(s.readyCount, s.invokingCount,
						s.notifiedCount, ArrayUtils.appended(s.hardFail, cause)))) {
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

	//TODO this RMIWrapper should be tested for robustness if a result cannot be serialized
	public static class MultiInnerTaskResultHolderRMIWrapper implements RMIWrapper {

		private Iterable<?> results;

		public MultiInnerTaskResultHolderRMIWrapper() {
		}

		public MultiInnerTaskResultHolderRMIWrapper(Iterable<?> results) {
			this.results = results;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			Iterator<?> it = results.iterator();
			while (it.hasNext()) {
				Object val = it.next();
				out.writeSerializedObject(val);
			}
			out.writeObject(null);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			List<Object> results = new ArrayList<>();
			while (true) {
				Object obj;
				try {
					obj = in.readObject();
				} catch (Exception e) {
					// failed to read or something
					results.add(new FailedInnerTaskOptionalResult<>("Failed to read inner task result.", e));
					continue;
				}
				if (obj == null) {
					break;
				}
				results.add(obj);
			}
			this.results = results;
		}

		@Override
		public Object resolveWrapped() {
			return results;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}

	public static class InnerTaskInstanceInvocationHandleRMIWrapper
			implements RMIWrapper, InnerTaskInstanceInvocationHandle {
		private InnerTaskInstanceInvocationHandle handle;

		public InnerTaskInstanceInvocationHandleRMIWrapper() {
		}

		public InnerTaskInstanceInvocationHandleRMIWrapper(InnerTaskInstanceInvocationHandle handle) {
			this.handle = handle;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(handle);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			handle = (InnerTaskInstanceInvocationHandle) in.readObject();
		}

		@Override
		public Object resolveWrapped() {
			return this;
		}

		@Override
		public Object getWrappedObject() {
			return handle;
		}

		@Override
		public void done() {
			callRMIAsyncAssert(handle, METHOD_DONE);
		}

		@Override
		public void doneNoMoreResults() {
			callRMIAsyncAssert(handle, METHOD_DONENOMORERESULTS);
		}

	}
}
