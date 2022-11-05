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
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.TaskInvocationManager.InnerTaskInvocationHandle;
import saker.build.task.TaskInvocationManager.InnerTaskInvocationListener;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;

public class InnerTaskInvocationManager implements Closeable {
	private static final int NO_COMPUTATION_TOKEN_FIXED_THREAD_POOL_SIZE = ComputationToken.getMaxTokenCount() * 3 / 2;
	//TODO there is a race condition after checking the task duplication predicate and notify the manager about execution start
	//     if the connection for the cluster breaks after shouldInvokeOnceMore() call, inner tasks may be left uninvoked, as
	//     it may not return true any more. See FixedTaskDuplicationPredicate, as that modifies the inner state of the predicate 

	protected final ExecutionContext executionContext;
	protected final Set<Reference<Thread>> weakInnerThreads = ConcurrentHashMap.newKeySet();

	public InnerTaskInvocationManager(ExecutionContext executionContext) {
		this.executionContext = executionContext;
	}

	private static class LocalInnerTaskInvocationHandle<R> implements InnerTaskInvocationHandle<R>, Runnable {
		private final InnerTaskInvocationListener listener;
		private final TaskFactory<R> taskFactory;
		private final TaskContext taskContext;
		private final Object computationTokenAllocator;
		private final int computationTokenCount;
		private final int maximumEnvironmentFactor;
		private final TaskDuplicationPredicate duplicationPredicate;
		private final InnerTaskInvocationManager invocationManager;

		private final ConcurrentPrependAccumulator<InnerTaskResultHolder<R>> results = new ConcurrentPrependAccumulator<>();
		private Reference<Thread> invokerThread;

		private volatile boolean duplicationCancelled;
		private volatile boolean shouldntInvokeOnceMore;

		@SuppressWarnings("rawtypes")
		private static final AtomicLongFieldUpdater<InnerTaskInvocationManager.LocalInnerTaskInvocationHandle> ALFU_resultCounter = AtomicLongFieldUpdater
				.newUpdater(InnerTaskInvocationManager.LocalInnerTaskInvocationHandle.class, "resultCounter");
		private static final long FLAG_LAST_RESULT_SENT = 1 << 63;
		/**
		 * MSB <br>
		 * 0 bit: set if the last result flag was sent. <br>
		 * 1-31 bit: the number of inner task invocations started. <br>
		 * 32-63 bit: the number of results made available.
		 */
		private volatile long resultCounter;

		public LocalInnerTaskInvocationHandle(InnerTaskInvocationManager invocationManager,
				InnerTaskInvocationListener listener, TaskFactory<R> taskfactory, TaskContext taskcontext,
				Object computationtokenallocator, int computationtokencount,
				TaskDuplicationPredicate duplicationPredicate, int maximumEnvironmentFactor) {
			this.invocationManager = invocationManager;
			this.listener = listener;
			this.taskFactory = taskfactory;
			this.taskContext = taskcontext;
			this.computationTokenAllocator = computationtokenallocator;
			this.computationTokenCount = computationtokencount;
			this.duplicationPredicate = duplicationPredicate;
			this.maximumEnvironmentFactor = maximumEnvironmentFactor;
		}

		@Override
		@SuppressWarnings("deprecation")
		public InnerTaskResultHolder<R> getResultIfPresent() {
			return results.take();
		}

		@Override
		public Iterable<? extends InnerTaskResultHolder<R>> getResultsIfAny() throws InterruptedException {
			return results.clearAndIterable();
		}

		@Override
		public void cancelDuplication() {
			duplicationCancelled = true;
			if (computationTokenCount > 0) {
				//don't need to perform notification if there's no computation tokens used
				ComputationToken.wakeUpWaiters(computationTokenAllocator);
			}
		}

		@Override
		public void waitFinish() throws InterruptedException {
			Thread t = ObjectUtils.getReference(invokerThread);
			if (t == null) {
				return;
			}
			t.join();
		}

		@Override
		public void interrupt() {
			//the invoker thread is interrupted, and that will interrupt any executing thread when joining
			ThreadUtils.interruptThread(ObjectUtils.getReference(invokerThread));
		}

		//TODO handle any RMI exceptions coming from result notifications

		//TODO the notifications should be sent in a locked way
		//as if the "last result" notification should arrive last, and the rmi methods can be 
		// called out of order if invoked on multiple threads

		private boolean startInvocationWithListener() {
			if (startInvocation()) {
				if (!listener.notifyTaskInvocationStart()) {
					reduceInvocation();
					return false;
				}
				return true;
			}
			return false;
		}

		private boolean startInvocation() {
			while (true) {
				long val = this.resultCounter;
				if (((val & FLAG_LAST_RESULT_SENT) == FLAG_LAST_RESULT_SENT)) {
					return false;
				}
				long invokedcount = val >>> 32;
				long nval = ((invokedcount + 1) << 32) | (val & 0xFFFFFFFFL);
				if (ALFU_resultCounter.compareAndSet(this, val, nval)) {
					return true;
				}
			}
		}

		private void reduceInvocation() {
			while (true) {
				long val = this.resultCounter;
				long invokedcount = (val >>> 32) & 0xFFFFFFFFL;
				if (invokedcount <= 0) {
					throw new IllegalStateException("Cant reduce invocation: " + Long.toHexString(val));
				}
				long nval = ((invokedcount - 1) << 32) | (val & 0xFFFFFFFFL);
				if (ALFU_resultCounter.compareAndSet(this, val, nval)) {
					return;
				}
			}
		}

		private int addResult(boolean last) {
			while (true) {
				long val = this.resultCounter;
				if (((val & FLAG_LAST_RESULT_SENT) == FLAG_LAST_RESULT_SENT)) {
					throw new IllegalStateException("Already sent last result: " + Long.toHexString(val));
				}
				long invokedcount = val >>> 32;
				long resultcount = (val & 0xFFFFFFFFL) + 1;
				if (resultcount > invokedcount) {
					throw new IllegalStateException(
							"Can't increase result count above invoked count: " + Long.toHexString(val));
				}
				long nval = ((invokedcount) << 32) | ((resultcount) & 0xFFFFFFFFL);
				if (last) {
					nval |= FLAG_LAST_RESULT_SENT;
				}
				if (ALFU_resultCounter.compareAndSet(this, val, nval)) {
					return (int) resultcount;
				}
			}
		}

		public void putResult(R result, boolean last) {
			results.add(new CompletedInnerTaskOptionalResult<>(result));
			int resultcount = addResult(last);
			listener.notifyResultReady(resultcount, last);
		}

		public void putExceptionResult(Throwable e, boolean last) {
			results.add(new FailedInnerTaskOptionalResult<>(e));
			int resultcount = addResult(last);
			listener.notifyResultReady(resultcount, last);
		}

		public void putFailureResult(Throwable e, boolean last) {
			results.add(new FailedInnerTaskOptionalResult<>(e));
			int resultcount = addResult(last);
			listener.notifyResultReady(resultcount, last);
		}

		public void noMoreResults() {
			while (true) {
				long val = this.resultCounter;
				if (((val & FLAG_LAST_RESULT_SENT) == FLAG_LAST_RESULT_SENT)) {
					//already set
					return;
				}
				long invokedcount = val >>> 32;
				long resultcount = (val & 0xFFFFFFFFL);
				if (resultcount != invokedcount) {
					throw new IllegalStateException("Can't set last result, invoked and result count doesn't equal: "
							+ invokedcount + " - " + resultcount + " in " + Long.toHexString(val));
				}
				long nval = val | FLAG_LAST_RESULT_SENT;
				if (ALFU_resultCounter.compareAndSet(this, val, nval)) {
					listener.notifyResultReady((int) resultcount, true);
					return;
				}
			}
		}

		@Override
		public void run() {
			try {
				runImpl();
			} catch (RMIRuntimeException e) {
				//failed to communicate
				//ignoreable.
			} finally {
				invocationManager.weakInnerThreads.remove(invokerThread);
			}
		}

		private void runDuplication(ThreadWorkPool workpool) {
			// 1 as one duplication was already performed once before this method is called
			int duplicatedcount = 1;
			while (!duplicationCancelled) {
				if (maximumEnvironmentFactor >= 1 && duplicatedcount >= maximumEnvironmentFactor) {
					// reached configured the maximum on this environment
					break;
				}
				if (!duplicationPredicate.shouldInvokeOnceMore()) {
					setShouldntInvokeOnceMore();
					return;
				}
				ComputationToken ct;
				try {
					ct = ComputationToken.requestAdditionalAbortable(computationTokenAllocator, computationTokenCount,
							() -> duplicationCancelled || shouldntInvokeOnceMore);
				} catch (InterruptedException e) {
					return;
				}
				if (ct == null) {
					return;
				}

				//the task is being duplicated, increase the counter
				++duplicatedcount;

				final ComputationToken fct = ct;
				workpool.offer(() -> {
					runDuplicatedInnerTaskWithComputationToken(fct, true);
				});
			}
		}

		private void runDuplicatedInnerTaskWithComputationToken(ComputationToken token, boolean releasable) {
			try {
				try (TaskContextReference contextref = TaskContextReference.createForInnerTask(taskContext)) {
					while (!duplicationCancelled) {
						if (Thread.interrupted()) {
							return;
						}
						if (releasable) {
							if (token.releaseIfOverAllocated()) {
								token = null;
								try {
									token = ComputationToken.requestAdditionalAbortable(computationTokenAllocator,
											computationTokenCount,
											() -> duplicationCancelled || shouldntInvokeOnceMore);
								} catch (InterruptedException e) {
									return;
								}
								if (token == null) {
									return;
								}
							}
						}
						if (!startInvocationWithListener()) {
							return;
						}
						Task<? extends R> task = createTaskForInvocation(false);
						if (task == null) {
							break;
						}
						runSingleInnerTask(contextref, task, false);
						if (!duplicationPredicate.shouldInvokeOnceMore()) {
							setShouldntInvokeOnceMore();
							break;
						}
					}
				} catch (RMIRuntimeException e) {
					//rmi exceptions shouldn't escape to the thread pool
				}
			} finally {
				if (token != null) {
					token.close();
				}
			}
		}

		private Task<? extends R> createTaskForInvocation(boolean last) {
			Task<? extends R> task;
			try {
				task = taskFactory.createTask(invocationManager.executionContext);
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				this.putFailureResult(e, last);
				return null;
			}
			if (task == null) {
				NullPointerException e = new NullPointerException(
						"Task factory created null task: " + taskFactory.getClass().getName());
				this.putFailureResult(e, true);
				return null;
			}
			return task;
		}

		private void setShouldntInvokeOnceMore() {
			shouldntInvokeOnceMore = true;
			if (computationTokenCount > 0) {
				//wake up the computation token waiters
				//as if their tokens were reallocated to other tasks, then they need to
				//be woken up to quit else they would wait for a new token with lower priority, therefore stalling
				ComputationToken.wakeUpWaiters(computationTokenAllocator);
			}
		}

		//suppress unused task context reference warning
		@SuppressWarnings("try")
		private void runImpl() {
			//implementation: allocate a core computation token for the invocation
			//    the core should succeed if there are available tokens, or the allocator is already used
			//    run tasks in the core computation token
			//    allocate any more secondary computation tokens for duplicated tasks if available
			//        this allocation of the secondary tokens should only succeed if there are available tokens,
			//        but not because the allocator is used again

			if (duplicationCancelled) {
				listener.notifyResultReady(0, true);
				return;
			}

			ComputationToken corecomptoken;
			try {
				corecomptoken = ComputationToken.requestAbortable(computationTokenAllocator, computationTokenCount,
						() -> duplicationCancelled);
			} catch (InterruptedException e) {
				listener.notifyResultReady(0, true);
				return;
			}
			if (corecomptoken == null) {
				listener.notifyResultReady(0, true);
				return;
			}

			try (ComputationToken coretokenres = corecomptoken) {
				if (this.duplicationPredicate != null) {
					//if the computation token is not at least 1, then use a fixed thread pool, to avoid sudden overallocation of threads
					//    that could get out of hand really quickly if not contained, or in case of faulty implementation
					String threadsname = "Inner-" + taskFactory.getClass().getSimpleName() + "-";
					try (ThreadWorkPool workpool = computationTokenCount < 1
							? ThreadUtils.newFixedWorkPool(NO_COMPUTATION_TOKEN_FIXED_THREAD_POOL_SIZE, threadsname)
							: ThreadUtils.newDynamicWorkPool(threadsname)) {
						workpool.offer(() -> {
							runDuplicatedInnerTaskWithComputationToken(coretokenres, false);
						});
						this.runDuplication(workpool);
					} finally {
						//TODO do not directly notify about no more results, but incorporate it in the task result notifications properly
						this.noMoreResults();
					}
				} else {
					if (!startInvocationWithListener()) {
						return;
					}
					Task<? extends R> task = createTaskForInvocation(true);
					if (task == null) {
						return;
					}
					try (TaskContextReference contextref = TaskContextReference.createForInnerTask(taskContext)) {
						runSingleInnerTask(contextref, task, true);
					}
				}
			}
		}

		private void runSingleInnerTask(TaskContextReference contextref, Task<? extends R> task, boolean last) {
			R result;
			try {
				InternalTaskBuildTrace btrace = ((InternalTaskContext) taskContext).internalGetBuildTrace()
						.startInnerTask(taskFactory);
				contextref.initTaskBuildTrace(btrace);
				try {
					result = task.run(taskContext);
				} catch (Throwable e) {
					btrace.setThrownException(e);
					throw e;
				} finally {
					btrace.endInnerTask();
				}
			} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
					| StackOverflowError e) {
				this.putExceptionResult(e, last);
				return;
			} catch (Throwable e) {
				try {
					this.putExceptionResult(e, last);
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				throw e;
			}
			this.putResult(result, last);
			return;
		}
	}

	private static class NotInvokedInnerTaskInvocationHandle<R> implements InnerTaskInvocationHandle<R> {
		@Override
		@SuppressWarnings("deprecation")
		public InnerTaskResultHolder<R> getResultIfPresent() {
			return null;
		}

		@Override
		public Iterable<? extends InnerTaskResultHolder<R>> getResultsIfAny() throws InterruptedException {
			return null;
		}

		@Override
		public void cancelDuplication() {
		}

		@Override
		public void waitFinish() throws InterruptedException {
		}

		@Override
		public void interrupt() {
		}
	}

	public <R> InnerTaskInvocationHandle<R> invokeInnerTask(TaskFactory<R> taskfactory, TaskContext taskcontext,
			InnerTaskInvocationListener listener, int computationtokencount,
			TaskDuplicationPredicate duplicationPredicate, int maximumEnvironmentFactor) throws Exception {
		//shouldInvokeOnceMore exception is propagated
		if (duplicationPredicate != null && !duplicationPredicate.shouldInvokeOnceMore()) {
			listener.notifyResultReady(0, true);
			return new NotInvokedInnerTaskInvocationHandle<>();
		}
		//the computation token allocator objects should be the task context identity
		//and should not be a caching RMI wrapper or other proxy objects.
		Object computationtokenallocator = ((InternalTaskContext) taskcontext).internalGetTaskContextIdentity();
		LocalInnerTaskInvocationHandle<R> resulthandle = new LocalInnerTaskInvocationHandle<>(this, listener,
				taskfactory, taskcontext, computationtokenallocator, computationtokencount, duplicationPredicate,
				maximumEnvironmentFactor);
		Thread thread = new Thread(resulthandle, "Inner-task: " + taskfactory.getClass().getName());
		resulthandle.invokerThread = new WeakReference<>(thread);
		weakInnerThreads.add(resulthandle.invokerThread);
		thread.start();
		return resulthandle;
	}

	@Override
	public void close() throws IOException {
		//join and possibly interrupt any still running tasks. that may happen if the RMI connection broke up
		//interrupt all threads, and then join them
		boolean interrupted = false;
		while (!weakInnerThreads.isEmpty()) {
			for (Iterator<Reference<Thread>> it = weakInnerThreads.iterator(); it.hasNext();) {
				Reference<Thread> ref = it.next();
				Thread t = ref.get();
				if (t == null) {
					it.remove();
					continue;
				}
				t.interrupt();
			}
			for (Iterator<Reference<Thread>> it = weakInnerThreads.iterator(); it.hasNext();) {
				Reference<Thread> ref = it.next();
				Thread t = ref.get();
				it.remove();
				if (t == null) {
					continue;
				}
				while (true) {
					try {
						t.join();
						break;
					} catch (InterruptedException e) {
						interrupted = true;
						//interrupt the threads once more just in case
						t.interrupt();
						for (Reference<Thread> tref : weakInnerThreads) {
							ThreadUtils.interruptThread(tref.get());
						}
						//the loop for joining the current one continues
					}
				}
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
