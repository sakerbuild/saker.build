package saker.build.task;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.TaskInvocationManager.InnerTaskInvocationHandle;
import saker.build.task.TaskInvocationManager.InnerTaskInvocationListener;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;

public class InnerTaskInvokerInvocationManager implements Closeable {
	private static final int NO_COMPUTATION_TOKEN_FIXED_THREAD_POOL_SIZE = ComputationToken.getMaxTokenCount() * 3 / 2;
	//TODO there is a race condition after checking the task duplication predicate and notify the manager about execution start
	//     if the connection for the cluster breaks after shouldInvokeOnceMore() call, inner tasks may be left uninvoked, as
	//     it may not return true any more. See FixedTaskDuplicationPredicate, as that modifies the inner state of the predicate 

	private ExecutionContext executionContext;
	private Set<Reference<Thread>> weakInnerThreads = ConcurrentHashMap.newKeySet();

	public InnerTaskInvokerInvocationManager(ExecutionContext executionContext) {
		this.executionContext = executionContext;
	}

	private class LocalInnerTaskInvocationHandle<R> implements InnerTaskInvocationHandle<R>, Runnable {

		private ConcurrentPrependAccumulator<InnerTaskResultHolder<R>> results = new ConcurrentPrependAccumulator<>();

		private Reference<Thread> invokerThread;
		private final InnerTaskInvocationListener listener;
		private final TaskFactory<R> taskFactory;
		private final TaskContext taskContext;
		private final Object computationTokenAllocator;
		private final int computationTokenCount;
		private final TaskDuplicationPredicate duplicationPredicate;

		private volatile boolean duplicationCancelled;

		public LocalInnerTaskInvocationHandle(InnerTaskInvocationListener listener, TaskFactory<R> taskfactory,
				TaskContext taskcontext, Object computationtokenallocator, int computationtokencount,
				TaskDuplicationPredicate duplicationPredicate) {
			this.listener = listener;
			this.taskFactory = taskfactory;
			this.taskContext = taskcontext;
			this.computationTokenAllocator = computationtokenallocator;
			this.computationTokenCount = computationtokencount;
			this.duplicationPredicate = duplicationPredicate;
		}

		@Override
		public InnerTaskResultHolder<R> getResultIfPresent() {
			return results.take();
		}

		@Override
		public void cancelDuplication() {
			duplicationCancelled = true;
			Object ctlock = ComputationToken.getAllocationLock();
			synchronized (ctlock) {
				ctlock.notifyAll();
			}
		}

		@Override
		public void waitFinish() throws InterruptedException {
			Thread t = invokerThread.get();
			if (t == null) {
				return;
			}
			t.join();
		}

		@Override
		public void interrupt() {
			//the invoker thread is interrupted, and that will interrupt any executing thread when joining
			ThreadUtils.interruptThread(invokerThread.get());
		}

		//TODO handle any RMI exceptions coming from result notifications

		//TODO the notifications should be sent in a locked way
		//as if the "last result" notification should arrive last, and the rmi methods can be 
		// called out of order if invoked on multiple threads
		public void putResult(R result, boolean lastresult) {
			results.add(new CompletedInnerTaskOptionalResult<>(result));
			listener.notifyResultReady(lastresult);
		}

		public void putExceptionResult(Throwable e, boolean lastresult) {
			results.add(new FailedInnerTaskOptionalResult<>(e));
			listener.notifyResultReady(lastresult);
		}

		@Override
		public void run() {
			try {
				runImpl();
			} catch (RMIRuntimeException e) {
				//failed to communicate
				//ignoreable.
			} finally {
				weakInnerThreads.remove(invokerThread);
			}
		}

		//suppress unused task context reference warning
		@SuppressWarnings("try")
		private void runDuplication(ThreadWorkPool workpool) {
			Object ctlock = ComputationToken.getAllocationLock();
			while (!duplicationCancelled) {
				try {
					if (!duplicationPredicate.shouldInvokeOnceMore()) {
						return;
					}
				} catch (RuntimeException e) {
					//some RMI exceptions or others may happen
					this.putExceptionResult(e, false);
					return;
				}
				ComputationToken ct;
				synchronized (ctlock) {
					while (true) {
						if (duplicationCancelled) {
							return;
						}
						ct = ComputationToken.requestIfAnyAvailableLocked(computationTokenAllocator,
								computationTokenCount);
						if (ct == null) {
							try {
								ctlock.wait();
							} catch (InterruptedException e) {
								this.putExceptionResult(e, false);
								return;
							}
							continue;
						}
						break;
					}
				}
				final ComputationToken fct = ct;
				workpool.offer(() -> {
					try (ComputationToken ctres = fct) {
						while (!duplicationCancelled) {
							if (Thread.interrupted()) {
								this.putExceptionResult(new InterruptedException("Inner task interrupted."), false);
								return;
							}
							Task<? extends R> task = taskFactory.createTask(executionContext);
							if (task == null) {
								this.putExceptionResult(
										new NullPointerException(
												"Task factory created null task: " + taskFactory.getClass().getName()),
										false);
								return;
							}
							if (!listener.notifyTaskInvocationStart()) {
								return;
							}
							try (TaskContextReference contextref = new TaskContextReference(taskContext)) {
								R result = task.run(taskContext);
								this.putResult(result, false);
							} catch (Exception e) {
								this.putExceptionResult(e, false);
							} catch (Throwable e) {
								try {
									this.putExceptionResult(e, false);
								} catch (Throwable e2) {
									e.addSuppressed(e2);
								}
								throw e;
							}
							try {
								if (!duplicationPredicate.shouldInvokeOnceMore()) {
									break;
								}
							} catch (RuntimeException e) {
								//some RMI exceptions or others may happen
								this.putExceptionResult(e, false);
								return;
							}
						}
					} catch (RMIRuntimeException e) {
						//rmi exceptions shouldn't escape to the thread pool
					}
				});
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

			Object ctlock = ComputationToken.getAllocationLock();

			ComputationToken corecomptoken;
			synchronized (ctlock) {
				while (true) {
					if (duplicationCancelled) {
						listener.notifyNoMoreResults();
						return;
					}
					corecomptoken = ComputationToken.requestIfAnyAvailableOrAlreadyAllocatedLocked(
							computationTokenAllocator, computationTokenCount);
					if (corecomptoken == null) {
						try {
							ctlock.wait();
						} catch (InterruptedException e) {
							this.putExceptionResult(e, true);
							return;
						}
						continue;
					}
					break;
				}
			}
			try (ComputationToken coretokenres = corecomptoken) {
				if (this.duplicationPredicate != null) {
					//if the computation token is not at least 1, then use a fixed thread pool, to avoid sudden overallocation of threads
					//    that could get out of hand really quickly if not contained, or in case of faulty implementation
					try (ThreadWorkPool workpool = computationTokenCount < 1
							? ThreadUtils.newFixedWorkPool(NO_COMPUTATION_TOKEN_FIXED_THREAD_POOL_SIZE,
									"Inner-" + taskFactory.getClass().getSimpleName() + "-")
							: ThreadUtils.newDynamicWorkPool("Inner-" + taskFactory.getClass().getSimpleName() + "-")) {
						workpool.offer(() -> {
							try {
								try (TaskContextReference contextref = new TaskContextReference(taskContext)) {
									while (!duplicationCancelled) {
										if (Thread.interrupted()) {
											this.putExceptionResult(new InterruptedException("Inner task interrupted."),
													false);
											return;
										}
										Task<? extends R> task = taskFactory.createTask(executionContext);
										if (task == null) {
											this.putExceptionResult(
													new NullPointerException("Task factory created null task: "
															+ taskFactory.getClass().getName()),
													false);
											return;
										}
										if (!listener.notifyTaskInvocationStart()) {
											return;
										}
										try {
											R result = task.run(taskContext);
											this.putResult(result, false);
										} catch (Exception e) {
											this.putExceptionResult(e, false);
										} catch (Throwable e) {
											try {
												this.putExceptionResult(e, false);
											} catch (Throwable e2) {
												e.addSuppressed(e2);
											}
											throw e;
										}
										try {
											if (!duplicationPredicate.shouldInvokeOnceMore()) {
												break;
											}
										} catch (RuntimeException e) {
											//some RMI exceptions or others may happen
											this.putExceptionResult(e, false);
											return;
										}
									}
								} finally {
									//close it here as well, before the actual pool quits
									coretokenres.close();
								}
							} catch (RMIRuntimeException e) {
								//any other RMI exceptions shouldn't escape to the thread pool
							}
						});
						this.runDuplication(workpool);
					} finally {
						//TODO do not directly notify about no more results, but incorporate it in the task result notifications properly
						listener.notifyNoMoreResults();
					}
				} else {
					Task<? extends R> task = taskFactory.createTask(executionContext);
					if (task == null) {
						this.putExceptionResult(new NullPointerException(
								"Task factory created null task: " + taskFactory.getClass().getName()), true);
						return;
					}
					if (!listener.notifyTaskInvocationStart()) {
						return;
					}
					try (TaskContextReference contextref = new TaskContextReference(taskContext)) {
						R result = task.run(taskContext);
						this.putResult(result, true);
					} catch (Exception e) {
						this.putExceptionResult(e, true);
					} catch (Throwable e) {
						try {
							this.putExceptionResult(e, true);
						} catch (Throwable e2) {
							e.addSuppressed(e2);
						}
						throw e;
					}
				}
			}
		}
	}

	private static class NotInvokedInnerTaskInvocationHandle<R> implements InnerTaskInvocationHandle<R> {
		@Override
		public InnerTaskResultHolder<R> getResultIfPresent() {
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
			InnerTaskInvocationListener listener, Object computationtokenallocator, int computationtokencount,
			TaskDuplicationPredicate duplicationPredicate) throws Exception {
		//shouldInvokeOnceMore exception is propagated
		if (duplicationPredicate != null && !duplicationPredicate.shouldInvokeOnceMore()) {
			listener.notifyNoMoreResults();
			return new NotInvokedInnerTaskInvocationHandle<>();
		}
		LocalInnerTaskInvocationHandle<R> resulthandle = new LocalInnerTaskInvocationHandle<>(listener, taskfactory,
				taskcontext, computationtokenallocator, computationtokencount, duplicationPredicate);
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
