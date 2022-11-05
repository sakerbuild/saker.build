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
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.exception.UnexpectedBuildSystemError;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.TaskExecutionManager.ManagerInnerTaskResults;
import saker.build.task.TaskExecutionManager.TaskExecutorContext;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.task.cluster.TaskInvokerInformation;
import saker.build.task.exception.ClusterEnvironmentSelectionFailedException;
import saker.build.task.exception.ClusterTaskExecutionFailedException;
import saker.build.task.exception.InnerTaskInitializationException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.task.exception.TaskResultSerializationException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
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

		/**
		 * Interrupts the inner task invocation thread.
		 */
		public void interrupt();
	}

	/**
	 * Coordinator side listener for inner task invocations.
	 */
	public interface InnerTaskInvocationListener {
		public static final Method METHOD_NOTIFYRESULTREADY = ReflectUtils
				.getMethodAssert(InnerTaskInvocationListener.class, "notifyResultReady", int.class, boolean.class);

		/**
		 * Notifies the listener about the total number of inner task results that are available.
		 * <p>
		 * The <code>resultcount</code> is the total number of results that were generated, not the currently available
		 * result count. Strictly monotonically increasing.
		 * 
		 * @param resultcount
		 *            The number of results.
		 * @param lastresult
		 *            <code>true</code> if no more results will be available.
		 */
		public void notifyResultReady(int resultcount, boolean lastresult);

		/**
		 * Notifies about the start of a new inner task.
		 * 
		 * @return <code>true</code> if the inner task can be executed.
		 */
		public boolean notifyTaskInvocationStart();
	}

	private final SakerEnvironmentImpl environment;
	private final ExecutionContextImpl executionContext;
	private final SakerEnvironment localTesterEnvironment;
	private final LazySupplier<?> clusterStartSupplier;
	private final Collection<? extends TaskInvokerFactory> invokerFactories;
	private final InnerTaskInvocationManager innerTaskInvoker;

	private ThreadUtils.ThreadWorkPool invokerPool;
	private Collection<TaskInvocationContextImpl> invocationContexts;

	private volatile boolean closed = false;

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
		TaskExecutionRequestImpl<R> request = null;
		if (capabilities.isRemoteDispatchable() && !ObjectUtils.isNullOrEmpty(invokerFactories)) {
			ensureClustersStarted();

			request = new TaskExecutionRequestImpl<>(invocationContexts, factory, capabilities, selectionresult,
					taskcontext);
			for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
				invocationcontext.addEvent(new TaskClusterExecutionEventImpl<>(invocationcontext, request));
			}
			//XXX we shouldn't wait for all clusters to respond, as if we can invoke the task locally, 
			//    it will be bottlenecked by the slowest cluster
			request.waitForResult();
			TaskInvocationResult<R> invocationresult = request.getTaskInvocationResult();
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
			//the local environment is not suitable, but a cluster was
			//cluster invokation didn't succeed, otherwise we wouldn't reach here
			//we can't invoke on the local env, so throw a cluster exception

			ClusterTaskExecutionFailedException thrownexc = new ClusterTaskExecutionFailedException(
					"Failed to execute task on cluster(s).");
			if (request != null) {
				//the execution request shouldn't be null at this point, but better check than fail
				addInvocationContextFailExceptions(thrownexc, request.contextFailExceptions.values());
			}
			throw thrownexc;
		}
		return invokeTaskRunningOnLocalEnvironment(factory, capabilities, taskcontext);
	}

	private static void addInvocationContextFailExceptions(Throwable thrownexc,
			Collection<? extends Throwable> exceptions) {
		Iterator<? extends Throwable> it = exceptions.iterator();
		if (it.hasNext()) {
			Throwable first = it.next();
			if (it.hasNext()) {
				//multiple exceptions, add them as suppressed exceptions to the thrown one
				thrownexc.addSuppressed(first);
				do {
					thrownexc.addSuppressed(it.next());
				} while (it.hasNext());
			} else {
				//only a single exception found, init the cause instead of suppressed exceptions
				thrownexc.initCause(first);
			}
		}
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
				return TaskInvocationResult.ofFailed(e);
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

	public <R> ManagerInnerTaskResults<R> invokeInnerTaskRunning(TaskFactory<R> taskfactory,
			SelectionResult selectionresult, int duplicationfactor, boolean duplicationcancellable,
			TaskExecutorContext<?> taskcontext, TaskDuplicationPredicate duplicationPredicate,
			TaskInvocationConfiguration configuration, Set<UUID> allowedenvironmentids, int maximumenvironmentfactor) {
		ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationhandles = new ConcurrentLinkedQueue<>();
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

		final Lock resultwaiterlock = ThreadUtils.newExclusiveLock();
		final Condition resultwaitercondition = resultwaiterlock.newCondition();
		boolean locallyrunnable = false;

		if (posttoenvironmentpredicate.test(environment.getEnvironmentIdentifier())) {
			boolean hasdiffs = hasAnyEnvironmentPropertyDifferenceWithLocalEnvironment(selectionresult);
			if (!hasdiffs) {
				ListenerInnerTaskInvocationHandler<R> listener = new ListenerInnerTaskInvocationHandler<>(
						resultwaiterlock, resultwaitercondition);
				try {
					InnerTaskInvocationHandle<R> handle = innerTaskInvoker.invokeInnerTask(taskfactory, taskcontext,
							listener, computationtokencount, duplicationPredicate, maximumenvironmentfactor);
					listener.handle = handle;
					postedtolocalenv = true;
					invocationhandles.add(listener);
					locallyrunnable = true;
				} catch (Exception e) {
					invokerexceptions = IOUtils.addExc(invokerexceptions, e);
				}
			}
		}
		InvokerTaskResultsHandler<R> fut = new InvokerTaskResultsHandler<>(taskcontext, invocationhandles,
				duplicationcancellable, locallyrunnable, resultwaiterlock, resultwaitercondition);

		if (configuration.isRemoteDispatchable() && !(postedtolocalenv && configuration.isPrefersLocalEnvironment())) {
			//don't post to build clusters if prefers the local environment and is being run on it
			ensureClustersStarted();
			InnerTaskExecutionRequestImpl<R> request = new InnerTaskExecutionRequestImpl<>(invocationContexts, fut,
					computationtokencount, taskfactory, taskcontext, duplicationPredicate, selectionresult,
					duplicationfactor, maximumenvironmentfactor);
			fut.setRequest(request);
			for (TaskInvocationContextImpl invocationcontext : invocationContexts) {
				if (!posttoenvironmentpredicate.test(invocationcontext.getEnvironmentIdentifier())) {
					//the event isnt posted to this invocation context, remove it from the request
					request.removeInvocationContext(invocationcontext);
					continue;
				}
				ListenerInnerTaskInvocationHandler<R> listener = new ListenerInnerTaskInvocationHandler<>(
						resultwaiterlock, resultwaitercondition);
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
				try {
					TaskInvoker pullingtaskinvoker = invokerfactory.createTaskInvoker(executionContext,
							taskinvokerinformation);
					taskinvocationcontext.run(pullingtaskinvoker);
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e) {
					taskinvocationcontext.close(e, false);
				} catch (Throwable e) {
					taskinvocationcontext.close(e, false);
					throw e;
				}
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
				//optional exception, don't pollute the exception stack if there's already another exception
				ic.close(cause, true);
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

	protected static void callRMIAsyncAssert(Object obj, Method m, Object... args) {
		try {
			RMIVariables.invokeRemoteMethodAsyncOrLocal(obj, m, args);
		} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
			throw new AssertionError(e);
		}
	}

	public static class TaskInvocationResult<R> {
		private final Optional<R> result;
		private final Throwable thrownException;

		private TaskInvocationResult(Optional<R> result, Throwable thrownException) {
			this.result = result;
			this.thrownException = thrownException;
		}

		public static <T> TaskInvocationResult<T> ofSuccessful(Optional<T> result) {
			return new TaskInvocationResult<>(result, null);
		}

		public static <T> TaskInvocationResult<T> ofFailed(Throwable thrownException) {
			return new TaskInvocationResult<>(null, thrownException);
		}

		public Optional<R> getResult() {
			return result;
		}

		public Throwable getThrownException() {
			return thrownException;
		}
	}

	public static class SelectionResult extends SerializationErrorRecovererExternalizable implements Externalizable {
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
		protected void writeImpl(ObjectOutput out) throws IOException {
			out.writeObject(environmentId);
			SerialUtils.writeExternalMap(out, qualifierEnvironmentProperties);
			SerialUtils.writeExternalCollection(out, modifiedEnvironmentProperties);
		}

		@Override
		protected void readImpl(ObjectInput in) throws IOException, ClassNotFoundException {
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

	/**
	 * A task invocation context provides access to the task invocation events of the build execution.
	 */
	public interface TaskInvocationContext {
		/**
		 * Polls one or more events from the build execution.
		 * 
		 * @return An iterable of events or <code>null</code> if the invocation context is closed.
		 * @throws InterruptedException
		 *             If the current thread is interrupted while polling.
		 */
		@RMIWrap(RMIArrayListWrapper.class)
		public Iterable<TaskInvocationEvent> poll() throws InterruptedException;
	}

	public interface InvocationRequest {
		public boolean isActive();

		/**
		 * Signals that the cluster failed to handle the request.
		 * <p>
		 * This is a more serious error, my signal:
		 * <ul>
		 * <li>serialization error of the underlying objects</li>
		 * <li>client code error, like {@link TaskFactory#createTask(ExecutionContext)} failing</li>
		 * <li>cluster thread interrupted</li>
		 * </ul>
		 * 
		 * @param invocationcontext
		 *            The invocation context.
		 * @param cause
		 *            The cause of the exception.
		 */
		public void fail(TaskInvocationContext invocationcontext, @RMISerialize Throwable cause);
	}

	/**
	 * Visitor for {@link TaskInvocationEvent} implementations.
	 */
	public interface TaskInvocationEventVisitor {
		public void visit(ExecutionEnvironmentSelectionEvent event);

		public void visit(ClusterExecutionEvent<?> event);

		public void visit(InnerClusterExecutionEvent<?> event);
	}

	public interface TaskInvocationEvent {
		/**
		 * Gets if this task invocation event is still active.
		 * <p>
		 * If the event is not active, it won't be returned in {@link TaskInvocationContext#poll()}.
		 * 
		 * @return <code>true</code> if active.
		 */
		public boolean isActive();

		public void fail(@RMISerialize Throwable cause);

		public void close(@RMISerialize Throwable cause);

		/**
		 * Accepts the argument visitor for this event.
		 * 
		 * @param visitor
		 *            The visitor.
		 */
		public void accept(TaskInvocationEventVisitor visitor);
	}

	public interface ExecutionEnvironmentSelectionEvent extends TaskInvocationEvent {
		public static final Method METHOD_FAILUNSUITABLE = ReflectUtils
				.getMethodAssert(ExecutionEnvironmentSelectionEvent.class, "failUnsuitable");

		@RMIForbidden
		public default Throwable getSerializationException() {
			return null;
		}

		@RMISerialize
		@RMICacheResult
		public TaskExecutionEnvironmentSelector getEnvironmentSelector();

		@RMISerialize
		@RMICacheResult
		public Map<? extends EnvironmentProperty<?>, ?> getDependentProperties();

		/**
		 * The environment selection was successful, and it is suitable for execution.
		 * 
		 * @param result
		 *            The selection result.
		 */
		public void succeed(@RMISerialize SelectionResult result);

		/**
		 * The environment is not suitable for execution.
		 */
		public void failUnsuitable();

		/**
		 * The environment is not suitable for execution and the check has failed with the argument exception.
		 * 
		 * @param e
		 *            The exception.
		 */
		public void failUnsuitable(@RMISerialize Throwable e);
	}

	public interface ClusterExecutionEvent<R> extends TaskInvocationEvent {
		public static final Method METHOD_FAILUNSUITABLE = ReflectUtils.getMethodAssert(ClusterExecutionEvent.class,
				"failUnsuitable");

		@RMIForbidden
		public default Throwable getSerializationException() {
			return null;
		}

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

		/**
		 * Checks if the execution can be started on the caller cluster.
		 * <p>
		 * If this method returns <code>true</code>, only {@link #executionSuccessful(Object)} and
		 * {@link #executionException(Throwable)} can be used to report an execution failure.
		 * 
		 * @return <code>true</code>if the execution can be started.
		 */
		public boolean startExecution();

		/**
		 * The execution of the given task was successful.
		 * 
		 * @param taskresult
		 *            The task result.
		 */
		public void executionSuccessful(@RMISerialize R taskresult);

		/**
		 * The execution of the task failed with an exception.
		 * 
		 * @param e
		 *            The exception.
		 */
		public void executionException(@RMISerialize Throwable e);

		/**
		 * Called when the cluster is not a suitable environment to invoke the task.
		 */
		public void failUnsuitable();

	}

	public interface InnerClusterExecutionEvent<R> extends TaskInvocationEvent, InnerTaskInvocationListener {
		public static final Method METHOD_FAILUNSUITABLE = ReflectUtils
				.getMethodAssert(InnerClusterExecutionEvent.class, "failUnsuitable");
		public static final Method METHOD_SETINVOCATIONHANDLE = ReflectUtils.getMethodAssert(
				InnerClusterExecutionEvent.class, "setInvocationHandle", InnerTaskInvocationHandle.class);

		@RMIForbidden
		public default Throwable getSerializationException() {
			return null;
		}

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
		public TaskDuplicationPredicate getDuplicationPredicate();

		/**
		 * Sets the invocation handle for the inner task invocations.
		 * <p>
		 * This is called when the invocation of inner tasks have started properly on the cluster.
		 * 
		 * @param resulthandle
		 *            The handle.
		 */
		public void setInvocationHandle(InnerTaskInvocationHandle<R> resulthandle);

		/**
		 * Notifies the event that the invocation failed to start due to an unexpected exception.
		 * 
		 * @param e
		 *            The exception.
		 */
		public void failInvocationStart(@RMISerialize Throwable e);

		/**
		 * Gets the environment selection result previously determined.
		 * 
		 * @return The selection result.
		 */
		@RMISerialize
		@RMICacheResult
		public SelectionResult getSelectionResult();

		/**
		 * The event receiver environment is not suitable for execution.
		 */
		public void failUnsuitable();
	}

	private abstract static class BaseInvocationRequest implements InvocationRequest {
		/**
		 * The set of invocation contexts that are still active.
		 */
		protected final Set<TaskInvocationContext> openContexts = ConcurrentHashMap.newKeySet();
		/**
		 * Contains the failure exceptions for each invocation context if they failed.
		 */
		protected final Map<TaskInvocationContext, Throwable> contextFailExceptions = new ConcurrentHashMap<>();

		public BaseInvocationRequest(Collection<? extends TaskInvocationContext> invocationcontexts) {
			openContexts.addAll(invocationcontexts);
		}

		@Override
		public boolean isActive() {
			return !openContexts.isEmpty();
		}

		@Override
		public void fail(TaskInvocationContext invocationcontext, Throwable cause) {
			if (cause != null) {
				Throwable present = contextFailExceptions.compute(invocationcontext, (k, v) -> v != null ? v : cause);
				if (present != cause) {
					present.addSuppressed(cause);
				}
			}
			removeInvocationContext(invocationcontext);
		}

		protected final void closeImpl(TaskInvocationContext invocationcontext, Throwable cause) {
			if (cause != null) {
				//only add the exception if there wasn't any previous exceptions
				contextFailExceptions.computeIfAbsent(invocationcontext, (k) -> cause);
			}
			removeInvocationContext(invocationcontext);
		}

		public abstract void close(TaskInvocationContext invocationcontext, Throwable cause);

		protected abstract void allClustersFailed();

		protected final void removeInvocationContext(TaskInvocationContext invocationcontext) {
			if (openContexts.remove(invocationcontext) && openContexts.isEmpty()) {
				allClustersFailed();
			}
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
			supplierResult.fail(this);
		}

		public void succeed(TaskInvocationContext invocationContext, SelectionResult result) {
			Throwable serialexc = result.getSerializationException();
			if (serialexc != null) {
				//deserialization of the selection result failed, consider the invocation context failed
				fail(invocationContext, new ClusterEnvironmentSelectionFailedException(
						"Failed to deserialize environment selection result.", serialexc));
				return;
			}
			supplierResult.setResult(Functionals.valSupplier(result));
		}

		@Override
		public void close(TaskInvocationContext invocationcontext, Throwable cause) {
			if (supplierResult.hasResult()) {
				//already has result, closing is irrelevant
				return;
			}
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

		public void failInvocationStart(TaskInvocationContext invocationcontext, Throwable e) {
			super.fail(invocationcontext, e);
		}

		@Override
		public void close(TaskInvocationContext invocationcontext, Throwable cause) {
			super.closeImpl(invocationcontext, cause);
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

		protected volatile TaskInvocationContext starterInvocationContext;

		protected final TaskFactory<R> factory;
		protected final TaskInvocationConfiguration capabilities;
		protected final SelectionResult selectionResult;
		protected final TaskContext taskContext;
		protected final TaskExecutionUtilities taskUtilities;

		protected TaskInvocationResult<R> invocationResult;
		protected final BooleanLatch finishedLatch = BooleanLatch.newBooleanLatch();

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
			invocationResult = TaskInvocationResult.ofSuccessful(Optional.ofNullable(taskresult));
			finishedLatch.signal();
		}

		public void executionException(Throwable e) {
			invocationResult = TaskInvocationResult.ofFailed(e);
			finishedLatch.signal();
		}

		@Override
		public void fail(TaskInvocationContext invocationcontext, Throwable cause) {
			if (isStartedExecution(invocationcontext)) {
				//wrap in a different exception so it is signalled, that this cluster started the execution, 
				//but failed for some other reason
				try {
					super.fail(invocationcontext, new ClusterTaskExecutionFailedException(cause));
				} finally {
					//always signal
					finishedLatch.signal();
				}
			} else {
				super.fail(invocationcontext, cause);
			}
		}

		@Override
		public void close(TaskInvocationContext invocationcontext, Throwable cause) {
			if (finishedLatch.isSignalled()) {
				//already finished, closing of the invocation context is okay
				return;
			}
			finishedLatch.signal();
			super.fail(invocationcontext, cause);
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

		public TaskInvocationResult<R> getTaskInvocationResult() {
			return invocationResult;
		}

	}

	@RMIWrap(ExecutionEnvironmentSelectionEventRMIWrapper.class)
	private static class ExecutionEnvironmentSelectionEventRMIWrapper extends SerializationErrorRecovererRMIWrapper
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
		protected void writeImpl(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(event);
			out.writeSerializedObject(event.getEnvironmentSelector());
			out.writeSerializedObject(event.getDependentProperties());
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void readImpl(RMIObjectInput in) throws IOException, ClassNotFoundException {
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
		public void failUnsuitable(Throwable e) {
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
			request.succeed(invocationContext, result);
		}

		@Override
		public void fail(Throwable cause) {
			request.fail(invocationContext, new ClusterEnvironmentSelectionFailedException(cause));
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
		public void failUnsuitable(Throwable e) {
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
	private static class InnerClusterExecutionEventRMIWrapper<R> extends SerializationErrorRecovererRMIWrapper
			implements RMIWrapper, InnerClusterExecutionEvent<R> {
		private InnerClusterExecutionEvent<R> event;
		private int computationTokenCount;
		private TaskFactory<R> taskFactory;
		private TaskContext taskContext;
		private TaskDuplicationPredicate duplicationPredicate;
		private SelectionResult selectionResult;
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
		protected void writeImpl(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(event);
			out.writeInt(event.getComputationTokenCount());
			out.writeRemoteObject(event.getTaskContext());
			out.writeInt(event.getMaximumEnvironmentFactor());
			out.writeObject(event.getDuplicationPredicate());
			out.writeSerializedObject(event.getTaskFactory());
			out.writeSerializedObject(event.getSelectionResult());
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void readImpl(RMIObjectInput in) throws IOException, ClassNotFoundException {
			event = (InnerClusterExecutionEvent<R>) in.readObject();
			computationTokenCount = in.readInt();
			taskContext = (TaskContext) in.readObject();
			maximumEnvironmentFactor = in.readInt();
			duplicationPredicate = (TaskDuplicationPredicate) in.readObject();
			taskFactory = (TaskFactory<R>) in.readObject();
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
		public void close(Throwable cause) {
			//XXX async?
			event.close(cause);
		}

		@Override
		public void accept(TaskInvocationEventVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public void notifyResultReady(int resultcount, boolean lastresult) {
			callRMIAsyncAssert(event, METHOD_NOTIFYRESULTREADY, resultcount, lastresult);
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
			request.failInvocationStart(invocationContext, e);
			invocationListener.notifyResultReady(0, true);

			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			responseLatch.signal();
		}

		@Override
		public void fail(Throwable cause) {
			//the cluster failed for some reason, the inner task invocation didn't proceed
			//exception is recorded in the base request
			//notify the listener about no more results
			request.fail(invocationContext, cause);
			invocationListener.notifyResultReady(0, true);

			AIFU_flags.updateAndGet(this, c -> c | FLAG_HAD_RESPONSE);
			responseLatch.signal();
		}

		@Override
		public void close(Throwable cause) {
			request.close(invocationContext, cause);
			invocationListener.closed(cause);

			AIFU_flags.updateAndGet(this, c -> c | (FLAG_HAD_RESPONSE | FLAG_CLOSED | FLAG_CANCEL_DUPLICATION));
			responseLatch.signal();
		}

		@Override
		public void failUnsuitable() {
			//TODO unsuitability exception?
			request.fail(invocationContext, null);
			invocationListener.notifyResultReady(0, true);

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
		public void notifyResultReady(int resultcount, boolean lastresult) {
			invocationListener.notifyResultReady(resultcount, lastresult);
		}

		@Override
		public boolean notifyTaskInvocationStart() {
			if (((this.flags & FLAG_CLOSED) == FLAG_CLOSED)) {
				return false;
			}
			return invocationListener.notifyTaskInvocationStart();
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

	private static class SerializationFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public SerializationFailureException(Throwable cause) {
			super(null, cause, false, false);
		}
	}

	private static class SerializationIndexFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public final int index;

		public SerializationIndexFailureException(int index, Throwable cause) {
			super(null, cause, false, false);
			this.index = index;
		}
	}

	private static class SerializationFailureMarker implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected Throwable cause;

		/**
		 * For {@link Externalizable}.
		 */
		public SerializationFailureMarker() {
		}

		public SerializationFailureMarker(Throwable cause) {
			this.cause = cause;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(cause);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			cause = (Throwable) in.readObject();
			throw new SerializationFailureException(cause);
		}
	}

	private static class SerializationIndexFailureMarker extends SerializationFailureMarker {
		private static final long serialVersionUID = 1L;

		protected int index;

		/**
		 * For {@link Externalizable}.
		 */
		public SerializationIndexFailureMarker() {
		}

		public SerializationIndexFailureMarker(int index, Throwable cause) {
			super(cause);
			this.index = index;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(index);
			out.writeObject(cause);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			index = in.readInt();
			cause = (Throwable) in.readObject();
			throw new SerializationIndexFailureException(index, cause);
		}
	}

	/**
	 * Base superclass for serializable objects that should gracefully handle serialization errrors.
	 * <p>
	 * The class transfers the failure exception of the serialization, and the reason is retrievable via
	 * {@link #getSerializationException()}.
	 * <p>
	 * Implementation notes: <br>
	 * The class internally uses {@link SerializationFailureException} and {@link SerializationFailureMarker} classes,
	 * which hold the serialization cause exception during transfer. This is necessary, as otherwise we may lose
	 * exceptions like {@link NotSerializableException} when the object that isn't serializable is being written in a
	 * recursive manner. See {@link ObjectOutputStream#writeObject(Object)} code, the exception is only written in case
	 * <code>depth == 0</code>.
	 * 
	 * @param <InStreamType>
	 *            The input stream type.
	 * @param <OutStreamType>
	 *            The output stream type.
	 */
	private static abstract class SerializationExceptionHandler<InStreamType extends ObjectInput, OutStreamType extends ObjectOutput> {
		protected transient Throwable serializationException;

		protected final void callWrite(OutStreamType out) throws IOException {
			try {
				writeImpl(out);
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				// some writing failed, maybe the task factory is not serializable?
				// don't throw the exception, not to fail the serialization
				// as that would fail RMI calls, and shut down the cluster calls
				// rather write the exception to the stream, that would be later
				// reported by the cluster
				try {
					out.writeObject(new SerializationFailureMarker(e));
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e2) {
					//this shouldn't really fail, unless the exception itself is not serializable
					//XXX can we do something with this exception?
					e2.addSuppressed(e);
					e2.printStackTrace();
				}
			}
		}

		protected final void callRead(InStreamType in) throws IOException, ClassNotFoundException {
			try {
				readImpl(in);
			} catch (SerializationFailureException e) {
				serializationException = e.getCause();
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				// if the deserialization of the stream data fails for some reason
				// then don't throw an exception, as that would cause the RMI calls to fail
				// in various way, and cause the cluster to shut down
				// instead we report the serialization exception, and the cluster
				// will call back with it as the failure reason
				serializationException = e;
				try {
					Object writeexc = in.readObject();
					if (writeexc instanceof Throwable) {
						e.addSuppressed((Throwable) writeexc);
					}
				} catch (SerializationFailureException sfe) {
					serializationException = sfe.getCause();
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e2) {
					e.addSuppressed(e2);
				}
			}
		}

		protected abstract void writeImpl(OutStreamType out) throws IOException;

		protected abstract void readImpl(InStreamType in) throws IOException, ClassNotFoundException;

		public final Throwable getSerializationException() {
			return serializationException;
		}
	}

	private static abstract class SerializationErrorRecovererRMIWrapper
			extends SerializationExceptionHandler<RMIObjectInput, RMIObjectOutput> implements RMIWrapper {
		@Override
		public final void writeWrapped(RMIObjectOutput out) throws IOException {
			callWrite(out);
		}

		@Override
		public final void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			callRead(in);
		}
	}

	private static abstract class SerializationErrorRecovererExternalizable
			extends SerializationExceptionHandler<ObjectInput, ObjectOutput> implements Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public final void writeExternal(ObjectOutput out) throws IOException {
			callWrite(out);
		}

		@Override
		public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			callRead(in);
		}
	}

	@RMIWrap(TaskClusterExecutionEventRMIWrapper.class)
	private static class TaskClusterExecutionEventRMIWrapper<R> extends SerializationErrorRecovererRMIWrapper
			implements RMIWrapper, ClusterExecutionEvent<R> {
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
		protected void writeImpl(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(event);
			out.writeInt(event.getComputationTokenCount());
			out.writeRemoteObject(event.getTaskContext());
			out.writeRemoteObject(event.getTaskUtilities());

			out.writeSerializedObject(event.getSelectionResult());
			out.writeSerializedObject(event.getTaskFactory());
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void readImpl(RMIObjectInput in) throws ClassNotFoundException, IOException {
			event = (ClusterExecutionEvent<R>) in.readObject();
			computationTokenCount = in.readInt();
			taskContext = (TaskContext) in.readObject();
			taskUtilities = (TaskExecutionUtilities) in.readObject();

			selectionResult = (SelectionResult) in.readObject();
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
		private final Lock eventLock = ThreadUtils.newExclusiveLock();
		private final Condition eventCondition = eventLock.newCondition();

		private final UUID environmentIdentifier;

		private List<TaskInvocationEvent> events = new ArrayList<>();
		private int pollEndIndex = 0;
		private volatile boolean closed = false;
		/**
		 * The exception that caused the closter to be closed.
		 * <p>
		 * If this is <code>null</code>, the the cluster closed in an orderly manner.
		 */
		private Throwable closeException;

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

		/**
		 * The cluster execution is aborted due to some unexpected exception.
		 * 
		 * @param e
		 *            The exception.
		 */
		public void clusterRunningAborted(Throwable e) {
			close(e, false);
		}

		/**
		 * The cluster is exiting after successfully finished running.
		 */
		public void clusterExit() {
			//null exception, the cluster closed orderly
			close(null, false);
		}

		public UUID getEnvironmentIdentifier() {
			return environmentIdentifier;
		}

		/**
		 * The invocation context is closed, and won't accept any more events.
		 * 
		 * @param e
		 *            The cause of the closing, or <code>null</code> if the cluster shut down orderly.
		 * @param optionalexc
		 *            If the provided exception is optional, and should only be set as the close exception if there's
		 *            none set yet.
		 */
		public void close(Throwable e, boolean optionalexc) {
			List<TaskInvocationEvent> evlist;
			Throwable closeex;

			final Lock lock = eventLock;
			lock.lock();
			try {
				evlist = this.events;

				closed = true;
				if (e != null) {
					if (closeException == null || !optionalexc) {
						//only set if there's no close exception yet,
						//or the exception is not optional
						closeException = IOUtils.addExc(closeException, e);
					}
				}
				events = Collections.emptyList();
				pollEndIndex = 0;

				eventCondition.signalAll();

				closeex = closeException;
			} finally {
				lock.unlock();
			}

			//close the remaining events of this invocation context
			Throwable failexc = createClosedException(closeex);
			for (TaskInvocationEvent ev : evlist) {
				ev.close(failexc);
			}
		}

		private IllegalStateException createClosedException(Throwable cexc) {
			return new IllegalStateException(
					"Task invocation context closed. (Env UUID: " + environmentIdentifier + ")", cexc);
		}

		public void addEvent(TaskInvocationEvent event) {
			Throwable closeex;

			final Lock lock = eventLock;
			lock.lock();
			try {
				if (!closed) {
					events.add(event);
					eventCondition.signal();
					return;
				}
				//else close the event below
				closeex = closeException;
			} finally {
				lock.unlock();
			}
			event.close(createClosedException(closeex));
		}

		@Override
		public Iterable<TaskInvocationEvent> poll() throws InterruptedException {
			final Lock lock = eventLock;
			lock.lock();
			try {
				if (closed) {
					return null;
				}
				List<TaskInvocationEvent> evlist = events;
				do {
					int eventcount = evlist.size();
					if (pollEndIndex >= eventcount) {
						eventCondition.await();
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
				} while (!closed);
			} finally {
				lock.unlock();
			}
			return null;
		}
	}

	private static class InvokerSelectionFutureSupplier implements Supplier<SelectionResult> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskInvocationManager.InvokerSelectionFutureSupplier, Supplier> ARFU_result = AtomicReferenceFieldUpdater
				.newUpdater(TaskInvocationManager.InvokerSelectionFutureSupplier.class, Supplier.class, "result");

		private volatile Supplier<? extends SelectionResult> result;
		//XXX instead of using a boolean latch, and a result field
		//    we could have a class that is the latch, and contains a generic field for the result as well
		//    something like a ReferenceLatch
		private final BooleanLatch resultReadyLatch = BooleanLatch.newBooleanLatch();

		private final Exception localSelectionException;

		public InvokerSelectionFutureSupplier(Exception localselectionexception) {
			this.localSelectionException = localselectionexception;
		}

		@Override
		public SelectionResult get() {
			Supplier<? extends SelectionResult> sr = result;
			if (sr != null) {
				return sr.get();
			}
			try {
				resultReadyLatch.await();
				sr = result;
				if (sr == null) {
					throw new IllegalStateException("Internal state error, result was set to null");
				}
				return sr.get();
			} catch (InterruptedException e) {
				//keep the interrupt flag on the current thread, 
				//as we're not throwing the InterruptedException directly
				Thread.currentThread().interrupt();
				IOUtils.addExc(e, localSelectionException);
				throw new TaskEnvironmentSelectionFailedException(e);
			}
		}

		public boolean hasResult() {
			return result != null;
		}

		public void fail(ExecutionEnvironmentInvocationRequestImpl request) {
			setResult(() -> {
				TaskEnvironmentSelectionFailedException thrownexc = new TaskEnvironmentSelectionFailedException(
						localSelectionException);
				for (Throwable failexc : request.contextFailExceptions.values()) {
					thrownexc.addSuppressed(failexc);
				}
				throw thrownexc;
			});
		}

		public void setResult(Supplier<? extends SelectionResult> result) {
			if (ARFU_result.compareAndSet(this, null, result)) {
				resultReadyLatch.signal();
			}
		}
	}

	private static class InvokerTaskResultsHandler<R> implements InnerTaskResults<R>, ManagerInnerTaskResults<R> {
		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<TaskInvocationManager.InvokerTaskResultsHandler> AIFU_failFlags = AtomicIntegerFieldUpdater
				.newUpdater(TaskInvocationManager.InvokerTaskResultsHandler.class, "failFlags");

		private static final int FAIL_FLAG_ALL_CLUSTERS_FAILED = 1 << 0;
		private static final int FAIL_FLAG_INIT_EXCEPTION_REPORTED = 1 << 1;

		private final TaskExecutorContext<?> taskContext;
		private final ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationHandles;
		private final Lock resultWaiterLock;
		private final Condition resultWaiterCondition;
		private final boolean duplicationCancellable;
		private final boolean locallyRunnable;

		private InnerTaskExecutionRequestImpl<R> request;

		private volatile int failFlags;

		public InvokerTaskResultsHandler(TaskExecutorContext<?> taskcontext,
				ConcurrentLinkedQueue<ListenerInnerTaskInvocationHandler<R>> invocationHandles,
				boolean duplicationCancellable, boolean locallyRunnable, Lock resultwaiterlock,
				Condition resultWaiterCondition) {
			this.taskContext = taskcontext;
			this.invocationHandles = invocationHandles;
			this.duplicationCancellable = duplicationCancellable;
			this.locallyRunnable = locallyRunnable;
			this.resultWaiterLock = resultwaiterlock;
			this.resultWaiterCondition = resultWaiterCondition;
		}

		public void setRequest(InnerTaskExecutionRequestImpl<R> request) {
			this.request = request;
		}

		public void allClustersFailed() {
			AIFU_failFlags.updateAndGet(this, v -> v | FAIL_FLAG_ALL_CLUSTERS_FAILED);
			resultWaiterLock.lock();
			try {
				resultWaiterCondition.signalAll();
			} finally {
				resultWaiterLock.unlock();
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
				{
					Iterator<ListenerInnerTaskInvocationHandler<R>> it = invocationHandles.iterator();
					if (!it.hasNext()) {
						//break the loop, will check if all clusters failed, and return appropriately
						break;
					}
					do {
						ListenerInnerTaskInvocationHandler<R> ih = it.next();
						try {
							InnerTaskResultHolder<R> presentres = ih.getResultIfPresent();
							if (presentres != null) {
								return presentres;
							}
						} catch (InterruptedException e) {
							throw e;
						} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError
								| AssertionError | StackOverflowError e) {
							//the handle should be removed, as we don't expect futher requests to succeed
							it.remove();
							return new FailedInnerTaskOptionalResult<>("Failed to retrieve inner task result.", e);
						}
					} while (it.hasNext());
				}
				ListenerInnerTaskInvocationHandler<R> availih = null;
				Iterator<ListenerInnerTaskInvocationHandler<R>> availihit = invocationHandles.iterator();
				if (!availihit.hasNext()) {
					//break the loop, will check if all clusters failed, and return appropriately
					break;
				}
				resultWaiterLock.lockInterruptibly();
				try {
					boolean hadhandler = false;
					do {
						ListenerInnerTaskInvocationHandler<R> ih = availihit.next();
						TaskResultReadyCountState readystate = ih.getReadyState();
						if (readystate.isResultAvailable()) {
							availih = ih;
							break;
						}
						if (readystate.isEnded()) {
							if (!readystate.isAnyMoreResultsExpected()) {
								availihit.remove();
							} else {
								hadhandler = true;
							}
							continue;
						}
						hadhandler = true;
					} while (availihit.hasNext());
					if (availih == null) {
						if (!hadhandler) {
							//no more handlers found
							//break the loop, will check if all clusters failed, and return appropriately
							break;
						}
						//clear the iterator so it is garbage collectable while waiting
						availihit = null;
						resultWaiterCondition.await();
						continue;
					}
				} finally {
					resultWaiterLock.unlock();
				}
				try {
					InnerTaskResultHolder<R> availpresent = availih.getResultIfPresent();
					if (availpresent != null) {
						return availpresent;
					}
				} catch (InterruptedException e) {
					throw e;
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e) {
					//the handle should be removed, as we don't expect futher requests to succeed
					availihit.remove();
					return new FailedInnerTaskOptionalResult<>("Failed to retrieve inner task result.", e);
				}
				//continue loop
			}
			//no more results are available, check if all clusters failed to signal initialization error
			//otherwise return with null
			if (!locallyRunnable) {
				while (true) {
					int failflags = this.failFlags;
					if (((failflags & FAIL_FLAG_ALL_CLUSTERS_FAILED) != FAIL_FLAG_ALL_CLUSTERS_FAILED)) {
						//all clusters didn't fail, don't thriw
						break;
					}
					if (((failflags & FAIL_FLAG_INIT_EXCEPTION_REPORTED) == FAIL_FLAG_INIT_EXCEPTION_REPORTED)) {
						//the exception was already reported
						break;
					}
					if (!AIFU_failFlags.compareAndSet(this, failflags, failflags | FAIL_FLAG_INIT_EXCEPTION_REPORTED)) {
						//failed to set the exception reported flag, try again
						continue;
					}
					InnerTaskInitializationException thrownexc = new InnerTaskInitializationException(
							"Failed to start inner task on the execution environments.");
					if (request != null) {
						addInvocationContextFailExceptions(thrownexc, request.contextFailExceptions.values());
					}
					throw thrownexc;
				}
			}
			return null;
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
		public static final TaskResultReadyCountState ZERO = new TaskResultReadyCountState(0, 0, 0, null, 0);

		/**
		 * The number of inner task results that are available.
		 */
		protected final int readyCount;
		/**
		 * The number of times the state was notified about newly available results.
		 * <p>
		 * Always less than or equals to {@link #invokingCount}.
		 */
		protected final int notifiedCount;
		/**
		 * The number of inner tasks invocations that were started.
		 */
		protected final int invokingCount;
		/**
		 * <code>null</code> if there was no hard failure yet. <br>
		 * Non-empty array if there are unretrieved exceptions. <br>
		 * Empty array if we had a hard failure, but the exceptions was relayed to the client. <br>
		 */
		protected final Throwable[] hardFail;

		/**
		 * Whether or not the invocation handler ended, that is, it expects no more inner task results to arrive, and
		 * the invocation handler can be removed for further results.
		 * <p>
		 * This can be set in case the inner task invoker signals that it won't send any more results.
		 */
		public static final int FLAG_ENDED = 1 << 0;
		/**
		 * Flag set if the task invoker hard failed.
		 */
		public static final int FLAG_HARDFAILED = 1 << 1;

		protected final int flags;

		public TaskResultReadyCountState(int readyCount, int invokingCount, int notifiedCount, Throwable[] hardFail,
				int flags) {
			this.readyCount = readyCount;
			this.invokingCount = invokingCount;
			this.notifiedCount = notifiedCount;
			this.hardFail = hardFail;
			this.flags = flags;
		}

		public TaskResultReadyCountState updateReadyNotificationCount(int notifcount, boolean lastresult) {
			int invokecount = invokingCount;
			int added = notifcount - this.notifiedCount;
			if (added < 0) {
				//the new notified count must be at least the old one
				throw new IllegalArgumentException(
						this + " update notification count: " + notifcount + " last: " + lastresult);
			}
			if (notifcount > invokecount) {
				//sanity check, can't notify more tasks than we invoked
				throw new IllegalStateException(
						this + " update notification count: " + notifcount + " last: " + lastresult);
			}
			int nflags = flags;
			if (lastresult) {
				nflags |= FLAG_ENDED;
			}
			Throwable[] hardfailarray = hardFail;
			if (lastresult && notifcount != invokecount) {
				//more task was invoked than we were notified for, treat the remaining number of results as hard failures
				int failcnt = invokecount - notifcount;
				int startidx;
				if (hardfailarray == null) {
					hardfailarray = new Throwable[failcnt];
					startidx = 0;
				} else {
					startidx = hardfailarray.length;
					hardfailarray = Arrays.copyOf(hardfailarray, startidx + failcnt);
				}
				Arrays.fill(hardfailarray, startidx, hardfailarray.length, new UnexpectedBuildSystemError(
						"Internal error, no result received for inner task invocation."));
			}
			return new TaskResultReadyCountState(readyCount + added, invokecount, notifcount, hardfailarray, nflags);
		}

		public TaskResultReadyCountState takeReady() {
			int nreadycount = readyCount - 1;
			if (nreadycount < 0) {
				//sanity check
				throw new IllegalStateException(this.toString());
			}
			return new TaskResultReadyCountState(nreadycount, invokingCount, notifiedCount, hardFail, flags);
		}

		public TaskResultReadyCountState addInvoking() {
			return new TaskResultReadyCountState(readyCount, invokingCount + 1, notifiedCount, hardFail, flags);
		}

		public TaskResultReadyCountState takeFirstException() {
			Throwable[] hardFail = this.hardFail;
			return new TaskResultReadyCountState(readyCount, invokingCount, notifiedCount,
					hardFail.length == 1 ? null : Arrays.copyOfRange(hardFail, 1, hardFail.length), flags);
		}

		public TaskResultReadyCountState end() {
			return new TaskResultReadyCountState(readyCount, invokingCount, notifiedCount, hardFail,
					flags | FLAG_ENDED);
		}

		public TaskResultReadyCountState addHardFail(Throwable failexc) {
			Throwable[] hardFail = this.hardFail;
			Throwable[] narray;
			if (hardFail == null) {
				narray = new Throwable[] { failexc };
			} else {
				narray = ArrayUtils.appended(hardFail, failexc);
			}
			//set the ended flag as well, because we don't expect any more result notifications due to the error
			return new TaskResultReadyCountState(readyCount, invokingCount, notifiedCount, narray,
					flags | FLAG_HARDFAILED | FLAG_ENDED);
		}

		public boolean isResultAvailable() {
			return readyCount > 0 || hardFail != null;
		}

		public boolean isAnyMoreResultsExpected() {
			return notifiedCount < invokingCount && !(((flags & FLAG_HARDFAILED) == FLAG_HARDFAILED));
		}

		/**
		 * Whether or not the task invoker has sent the last result notification.
		 */
		public boolean isEnded() {
			return ((flags & FLAG_ENDED) == FLAG_ENDED);
		}

		/**
		 * Whether or not a hard exception was received, meaning a fatal failure in the task invoker.
		 */
		public boolean isHardFailed() {
			return ((flags & FLAG_HARDFAILED) == FLAG_HARDFAILED);
		}

		public boolean isEndedOrHardFailed() {
			return (flags & (FLAG_ENDED | FLAG_HARDFAILED)) != 0;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[readyCount=");
			builder.append(readyCount);
			builder.append(", notifiedCount=");
			builder.append(notifiedCount);
			builder.append(", invokingCount=");
			builder.append(invokingCount);
			builder.append(", hardFail=");
			builder.append(Arrays.toString(hardFail));
			builder.append(", flags=");
			builder.append(Integer.toHexString(flags));
			builder.append("]");
			return builder.toString();
		}
	}

	private static class ListenerInnerTaskInvocationHandler<R> implements InnerTaskInvocationListener {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskInvocationManager.ListenerInnerTaskInvocationHandler, TaskResultReadyCountState> ARFU_readyState = AtomicReferenceFieldUpdater
				.newUpdater(TaskInvocationManager.ListenerInnerTaskInvocationHandler.class,
						TaskResultReadyCountState.class, "readyState");

		private final Lock resultWaiterLock;
		private final Condition resultWaiterCondition;

		private volatile TaskResultReadyCountState readyState = TaskResultReadyCountState.ZERO;

		protected InnerTaskInvocationHandle<R> handle;

		public ListenerInnerTaskInvocationHandler(Lock resultWaiterLock, Condition resultWaiterCondition) {
			this.resultWaiterLock = resultWaiterLock;
			this.resultWaiterCondition = resultWaiterCondition;
		}

		public void cancelDuplicationOptionally() {
			if (readyState.isEnded()) {
				return;
			}
			handle.cancelDuplication();
		}

		public void waitFinish() throws InterruptedException {
			if (readyState.isEnded()) {
				return;
			}
			handle.waitFinish();
		}

		public void interrupt() {
			if (readyState.isEnded()) {
				return;
			}
			handle.interrupt();
		}

		public TaskResultReadyCountState getReadyState() {
			return readyState;
		}

		public InnerTaskResultHolder<R> getResultIfPresent() throws InterruptedException {
			while (true) {
				TaskResultReadyCountState s = this.readyState;
				if (s.readyCount > 0) {
					TaskResultReadyCountState nstate = s.takeReady();
					if (!ARFU_readyState.compareAndSet(this, s, nstate)) {
						continue;
					}
					InnerTaskResultHolder<R> res = handle.getResultIfPresent();
					if (res == null) {
						throw new AssertionError("Failed to retrieve inner task result.");
					}
					return res;
				}
				if (!ObjectUtils.isNullOrEmpty(s.hardFail)) {
					TaskResultReadyCountState nstate = s.takeFirstException();
					if (!ARFU_readyState.compareAndSet(this, s, nstate)) {
						continue;
					}
					Throwable firsthardfailexc = s.hardFail[0];
					return new FailedInnerTaskOptionalResult<>(firsthardfailexc);
				}
				//no results ready
				return null;
			}
		}

		@Override
		public void notifyResultReady(int resultcount, boolean lastresult) {
			TaskResultReadyCountState nstate;
			while (true) {
				TaskResultReadyCountState state = this.readyState;
				boolean stateended = state.isEnded();
				int statenotified = state.notifiedCount;
				if (stateended) {
					if (resultcount > statenotified) {
						//the state already ended, so we cannot receive a larger result count than what we had when we ended it
						throw new IllegalArgumentException("Cannot update result count for already ended state: "
								+ state + " with update result: " + resultcount);
					}
					if (lastresult && resultcount != statenotified) {
						//if the state was already notified, and we also receive one more last result notification
						//then the new count and the state count must equal, otherwise we've seen different counts for the ended state
						//which signals some inconsistency
						throw new IllegalArgumentException(
								"Inconsistent task result notification count for ended state: " + resultcount + " and "
										+ statenotified);
					}
				}
				if (statenotified < resultcount || stateended != lastresult) {
					nstate = state.updateReadyNotificationCount(resultcount, lastresult);
					if (ARFU_readyState.compareAndSet(this, state, nstate)) {
						break;
					}
				} else {
					//nothing was updated
					return;
				}
			}

			resultWaiterLock.lock();
			try {
				if (nstate.isEnded()) {
					//signal all if the new state is ended
					resultWaiterCondition.signalAll();
				} else {
					resultWaiterCondition.signal();
				}
			} finally {
				resultWaiterLock.unlock();
			}
		}

		public void notifyStateChange() {
			resultWaiterLock.lock();
			try {
				resultWaiterCondition.signalAll();
			} finally {
				resultWaiterLock.unlock();
			}
		}

		@Override
		public boolean notifyTaskInvocationStart() {
			while (true) {
				TaskResultReadyCountState s = this.readyState;
				if (s.isEndedOrHardFailed()) {
					//the state has ended
					//  or
					//we had a hard failure, no more invocations
					return false;
				}
				if (ARFU_readyState.compareAndSet(this, s, s.addInvoking())) {
					return true;
				}
			}
		}

		public void closed(Throwable cause) {
			hardFailed(cause);
		}

		public void hardFailed(Throwable cause) {
			while (true) {
				TaskResultReadyCountState s = readyState;
				TaskResultReadyCountState nstate = s.addHardFail(cause);
				if (ARFU_readyState.compareAndSet(this, s, nstate)) {
					break;
				}
				continue;
			}
			resultWaiterLock.lock();
			try {
				resultWaiterCondition.signalAll();
			} finally {
				resultWaiterLock.unlock();
			}
		}
	}

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

			for (int index = 0; it.hasNext(); ++index) {
				Object val = it.next();
				try {
					out.writeSerializedObject(val);
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e) {
					//the serialization of this task result failed
					//write a marker of the failure
					try {
						out.writeObject(new SerializationIndexFailureMarker(index, e));
					} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
							| StackOverflowError e2) {
						//this shouldn't really fail, unless the exception itself is not serializable
						//XXX can we do something with this exception?
						e2.addSuppressed(e);
						e2.printStackTrace();
					}
				}
			}
			out.writeObject(null);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			List<InnerTaskResultHolder<?>> results = new ArrayList<>();
			while (true) {
				InnerTaskResultHolder<?> obj;
				try {
					obj = (InnerTaskResultHolder<?>) in.readObject();
					if (obj == null) {
						break;
					}
				} catch (SerializationIndexFailureException e) {
					if (e.index >= results.size()) {
						//sanity check
						//shouldn't happen, but better throw an exception than fail otherwise
						IOException ioe = new IOException("Invalid serialization protocol, failure index: " + e.index
								+ " is out of bounds for results size: " + results.size());
						ioe.addSuppressed(e);
						throw ioe;
					}
					results.set(e.index, new FailedInnerTaskOptionalResult<>("Failed to read inner task result.",
							new TaskResultSerializationException(e.getCause())));
					continue;
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e) {
					// failed to read probably during the initial write.
					// we expect to read a SerializationIndexFailureMarker later, but place the
					// following object to the result list at least as a placeholder
					obj = new FailedInnerTaskOptionalResult<>("Failed to read inner task result.",
							new TaskResultSerializationException(e));
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

}
