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
package saker.build.thirdparty.saker.util.thread;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ArrayIterator;
import saker.build.thirdparty.saker.util.ConcurrentAppendAccumulator;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;

/**
 * Utility class containing functions and classes related to threads and their manipulation.
 * <p>
 * The class contains support for thread pools, running multiple worker functions concurrently, dealing with threads,
 * and others.
 */
public class ThreadUtils {
	//TODO handle if the context suppliers throw an exception
	/**
	 * Functional interface similar to {@link Consumer}, but is capable of throwing an arbitrary exception.
	 * 
	 * @param <T>
	 *            The type of the argument that this consumer works on.
	 */
	@FunctionalInterface
	public interface ThrowingConsumer<T> {
		/**
		 * Executes the action for the given argument.
		 * 
		 * @param value
		 *            The argument.
		 * @throws Exception
		 *             In case of failure.
		 */
		public void accept(T value) throws Exception;
	}

	/**
	 * Functional interface similar to {@link ThrowingConsumer} that can take an another context argument for its
	 * execution.
	 * <p>
	 * A context variable is something that is instantiated once for multiple calls of a consumer.
	 * 
	 * @param <T>
	 *            The type of the argument that this consumer works on.
	 * @param <C>
	 *            The type of the context object.
	 */
	@FunctionalInterface
	public interface ThrowingContextConsumer<T, C> {
		/**
		 * Executes the action for the given argument and context object.
		 * 
		 * @param value
		 *            The argument.
		 * @param context
		 *            The context object.
		 * @throws Exception
		 *             In case of failure.
		 */
		public void accept(T value, C context) throws Exception;
	}

	/**
	 * Interface for a thread pool that is capable of executing tasks offered to it.
	 * <p>
	 * An instance of a thread pool schedules the offered tasks to it in an implementation dependent manner.
	 * <p>
	 * The thread pools can be {@linkplain #close() closed}, in which case the pending tasks will be waited for, and the
	 * pool will no longer accept task offerings to it.
	 * <p>
	 * The thread pools can be reset, in which case it will wait for all pending tasks to complete, but unlike
	 * {@link #close()}, it will be reuseable. Spawned threads will probably stay alive when resetting a thread pool.
	 * <p>
	 * Thread pools can be signaled to {@linkplain #exit() exit}, in which case it will not wait for the pending tasks,
	 * but will prevent adding any more tasks if the pending ones are finished. Thread pools can be reset after
	 * signaling exit.
	 * <p>
	 * Note, that some thread pool implementations are allowed to be offered tasks to them even after they're closed.
	 * However, if they do so, they must handle their graceful teardown without explicit second closing.
	 * <p>
	 * Some thread pool implementations may allow resetting them after they've been closed. In this case, the thread
	 * pool must be closed again if the resetting is successful. In general, closing can be called multiple times, and
	 * every reset call should be matched to a corresponding close call.
	 * <p>
	 * This interface is not designed to be implemented by users. Clients should instantiate thread pools using the
	 * static factory methods in the {@link ThreadUtils} class.
	 */
	public static interface ThreadWorkPool extends AutoCloseable {
		/**
		 * Offers a task to be executed in this thread pool.
		 * <p>
		 * The offered task is scheduled to be executed in an implementation dependent manner. It may be executed right
		 * away, it may be delegated to an other thread, may be delayed to an other time, or in any other ways that the
		 * implementation sees fit.
		 * <p>
		 * If users want to run a task that returns some result, they need to employ their own synchronization
		 * mechanics.
		 * 
		 * @param task
		 *            The task to be executed by this work pool.
		 * @throws NullPointerException
		 *             If the task is <code>null</code>.
		 * @throws IllegalStateException
		 *             If the thread pool has been closed, or exited without resetting.
		 */
		//XXX maybe implement some utility functions in ThreadUtils to help synchronization for returning results from a task?
		public void offer(ThrowingRunnable task) throws NullPointerException, IllegalStateException;

		/**
		 * Closes the thread pool in a non-interruptible, cancelling way.
		 * <p>
		 * This method will wait for all pending tasks to finish, and then closes the thread pool completely for further
		 * use.
		 * <p>
		 * During the closing, new tasks can still be offered to the thread pool, only if there are still any tasks
		 * running. If there are no tasks running, and closing is requested, no more tasks can be offered to the thread
		 * pool.
		 * <p>
		 * A closed thread pool cannot be {@linkplain #reset() reset}.
		 * <p>
		 * If the current thread is interrupted, it will be treated as the tasks in the thread pool are cancelled. The
		 * currently executing task threads will be interrupted, and the waiting for the tasks will continue. The
		 * interrupted flag of the current thread is stored, and the current thread is reinterrupted if necessary. If
		 * any task was cancelled due to interruption, a {@link ParallelExecutionException} will signal that
		 * accordingly.
		 * 
		 * @throws ParallelExecutionException
		 *             If any of the tasks threw an exception. If this exception is thrown, the thread pool is
		 *             considered to be successfully closed.
		 * @throws IllegalThreadStateException
		 *             If the thread pool is being closed on a thread that is part of the thread pool. I.e. the pool is
		 *             being closed from a task thread.
		 */
		@Override
		public void close() throws ParallelExecutionException, IllegalThreadStateException;

		/**
		 * Closes the thread pool in an interruptible way.
		 * <p>
		 * This method will wait for all pending tasks to finish, and then closes the thread pool completely for further
		 * use.
		 * <p>
		 * During the closing, new tasks can still be offered to the thread pool, only if there are still any tasks
		 * running. If there are no tasks running, and closing is requested, no more tasks can be offered to the thread
		 * pool.
		 * <p>
		 * A closed thread pool cannot be {@linkplain #reset() reset}.
		 * <p>
		 * If the current thread is interrupted, the {@link InterruptedException} is propagated to the caller. This
		 * means that the thread pool wasn't closed, but that it is signaled to exit. Pending tasks are not interrupted.
		 * 
		 * @throws ParallelExecutionException
		 *             If any of the tasks threw an exception. If this exception is thrown, the thread pool is
		 *             considered to be successfully closed.
		 * @throws InterruptedException
		 *             If the current thread was interrupted while waiting for the pending tasks.
		 * @throws IllegalThreadStateException
		 *             If the thread pool is being closed on a thread that is part of the thread pool. I.e. the pool is
		 *             being closed from a task thread.
		 */
		public void closeInterruptible()
				throws ParallelExecutionException, InterruptedException, IllegalThreadStateException;

		/**
		 * Sends an exit signal to the thread pool.
		 * <p>
		 * The method returns immediately, only sends a signal that the thread pool should exit if no more tasks are
		 * being scheduled.
		 * <p>
		 * After exiting, the thread pool can be considered as closed, however {@link #reset()} still can be called to
		 * return the thread pool to a working state.
		 * <p>
		 * Similar to {@link #close()}, new tasks can still be offered to the thread pool, only if there are still any
		 * tasks running. If there are no tasks running, no more tasks can be offered to the thread pool, unless
		 * {@link #reset()} is called.
		 */
		public void exit();

		/**
		 * Resets the thread pool in a non-interruptible way so it accepts task offered to it.
		 * <p>
		 * Resetting will wait for pending tasks to finish (if any), and throw the exception from the tasks to the
		 * caller. If a {@link ParallelExecutionException} is thrown, the thread pool is considered to be successfully
		 * reset, but the tasks threw an exception during their execution.
		 * <p>
		 * This method handles if the current thread is interrupted while waiting for the tasks to finish. Unlike
		 * {@link #close()}, interrupting the current thread <b>will not</b> interrupt the task executor threads, so
		 * they won't be cancelled by that. The interrupted flag of the current thread is stored, and the current thread
		 * is reinterrupted if necessary.
		 * <p>
		 * Resetting a non-closed thread pool or resetting a thread pool multiple times has no effect.
		 * <p>
		 * <b>Warning:</b> Call this method only, if you can ensure that the offered tasks will properly finish, and
		 * will not deadlock. As this method basically ignores the interruption of the current thread, this can result
		 * in deadlocking, or leaving unclosed resources in the JVM.
		 * 
		 * @throws ParallelExecutionException
		 *             If any of the tasks threw an exception. If this exception is thrown, the thread pool is
		 *             considered to be successfully reset.
		 * @throws IllegalStateException
		 *             If the thread pool have been explicitly closed and cannot be reset.
		 * @throws IllegalThreadStateException
		 *             If the thread pool is being closed on a thread that is part of the thread pool. I.e. the pool is
		 *             being closed from a task thread.
		 */
		public void reset() throws ParallelExecutionException, IllegalStateException, IllegalThreadStateException;

		/**
		 * Resets the thread pool in an interruptible way so it accepts tasks offered to it.
		 * <p>
		 * Resetting will wait for pending tasks to finish (if any), and throw the exception from the tasks to the
		 * caller. If a {@link ParallelExecutionException} is thrown, the thread pool is considered to be successfully
		 * reset, but the tasks threw an exception during their execution.
		 * <p>
		 * If the current method is interrupted during the waiting for the task, the {@link InterruptedException} is
		 * propagated to the caller. This means that there are still pending tasks in the thread pool. If this happens,
		 * tasks can be still offered to the thread pool, as it is still in an alive state.
		 * <p>
		 * Resetting a non-closed thread pool or resetting a thread pool multiple times has no effect.
		 * 
		 * @throws ParallelExecutionException
		 *             If any of the tasks threw an exception. If this exception is thrown, the thread pool is
		 *             considered to be successfully reset.
		 * @throws InterruptedException
		 *             If the current thread was interrupted while waiting for the pending tasks.
		 * @throws IllegalStateException
		 *             If the thread pool have been explicitly closed.
		 * @throws IllegalThreadStateException
		 *             If the thread pool is being closed on a thread that is part of the thread pool. I.e. the pool is
		 *             being closed from a task thread.
		 */
		public void resetInterruptible() throws ParallelExecutionException, InterruptedException, IllegalStateException,
				IllegalThreadStateException;
	}

	/**
	 * Interface provided to task runner implementation which can signal cancellation of the operation.
	 * <p>
	 * If a task running is cancelled, the {@link #isCancelled()} method should return <code>true</code>.
	 */
	public interface OperationCancelMonitor {
		/**
		 * Checks if the associated operation have been cancelled.
		 * 
		 * @return <code>true</code> if it has been cancelled.
		 */
		public boolean isCancelled();
	}

	/**
	 * Builder class for executing various tasks with the specified configuration concurrently.
	 * <p>
	 * This class can be used to configure the concurrent execution of some specified tasks. The configuration includes
	 * setting the appropriate {@link ThreadGroup}, the number of threads to use, cancellation monitors, and others.
	 * <p>
	 * This class acts as a builder, in the sense that it serves as a configuration entity for the executed tasks. This
	 * class is reuseable, meaning that running methods can be called multiple times on it.
	 * <p>
	 * All running methods in this class throw {@link ParallelExecutionException} if the tasks have failed in any way.
	 * The thrown exception can be examined to determine the real cause of the exception.
	 * <p>
	 * Some of the methods have simplified corresponding functions in {@link ThreadUtils} that use the default
	 * configuration for the execution, and require no {@link ParallelRunner} instance.
	 * <p>
	 * Note, that although this class is designed to run tasks concurrently, that doesn't always imply that the posted
	 * tasks will run concurrently. E.g. if only a single task needs to run, it will be executed on the current thread
	 * without creating a new one for it.
	 * <p>
	 * The class supports running simple {@link Runnable Runnables} concurrently. In that case they will have no extra
	 * handling by the executor, and they will simply run concurrently. See {@link #runRunnables(Runnable...)}.
	 * <p>
	 * The class supports running tasks concurrently for given work items. The work items will be passed to the given
	 * worker tasks which should use the passed argument to do their work on it. See
	 * {@link #runItems(Object[], ThrowingConsumer)}.
	 * <p>
	 * The class supports running tasks concurrently for given work items, and using thread specific context objects.
	 * The context objects are allocated for each worker thread and will be passed to the worker task alongsite the work
	 * item. The context objects can be used as a cache, in order to reduce the amount of object allocations, or others.
	 * Note, that even though context objects are cached, the tasks should work properly even if a new context object is
	 * allocated for each invocation. See {@link #runContextItems(Object[], Supplier, ThrowingContextConsumer)}.
	 * <p>
	 * The items in the runner methods may be specified by a {@link Supplier} instance. In that case the supplier will
	 * be repeatedly called and new tasks will be started for the results, until a <code>null</code> is returned by the
	 * {@link Supplier}. The supplier <b>must</b> return a <code>null</code> eventually, else an infinite number of
	 * tasks will be started, which most likely deadlock the whole operating system or crash the JVM. (As this can
	 * result creating possibly infinite number of threads.)
	 * <p>
	 * The <code>run</code> methods in this class supports the throwing of {@link ParallelExecutionAbortedException}. If
	 * a worker function throws such an exception, the further execution of tasks will be cancelled, and the exception
	 * will be thrown to the caller.
	 * <p>
	 * Use {@link ThreadUtils#parallelRunner()} to instantiate a new builder.
	 */
	public static final class ParallelRunner {
		private ThreadGroup threadGroup;
		private int threadCount = -1;
		private String namePrefix;
		private OperationCancelMonitor monitor;

		protected ParallelRunner() {
		}

		/**
		 * Sets the thread group that should be used when starting new threads.
		 * <p>
		 * Note, that the tasks are not always executed on a thread that is running in the given thread group, so worker
		 * functions shouldn't rely on the thread group being a specific instance. See {@link ParallelRunner}
		 * documentation for examples.
		 * 
		 * @param threadGroup
		 *            The thread group or <code>null</code> to use the same thread group as the caller thread. (The
		 *            caller which calls the <code>run</code> methods, not the caller that calls this method.)
		 * @return <code>this</code>
		 */
		public ParallelRunner setThreadGroup(ThreadGroup threadGroup) {
			this.threadGroup = threadGroup;
			return this;
		}

		/**
		 * Sets the thread count that should be used when running multiple tasks concurrently.
		 * <p>
		 * Negative values mean the {@linkplain ThreadUtils#setInheritableDefaultThreadFactor(int) default value}.
		 * <p>
		 * 0 or 1 means that no threads should be spawned, and all run tasks should use the caller thread.
		 * <p>
		 * Any other values mean that at most that amount of threads can be spawned to run the tasks.
		 * 
		 * @param threadCount
		 *            The thread count.
		 * @return <code>this</code>
		 */
		public ParallelRunner setThreadCount(int threadCount) {
			this.threadCount = threadCount;
			return this;
		}

		/**
		 * Sets the name prefix that should be used for any started threads.
		 * 
		 * @param namePrefix
		 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
		 *            thread number identifier will be appended to it by the runner.
		 * @return <code>this</code>
		 */
		public ParallelRunner setNamePrefix(String namePrefix) {
			this.namePrefix = namePrefix;
			return this;
		}

		/**
		 * Sets the cancellation monitor for the running.
		 * <p>
		 * If the cancellation monitor returns <code>true</code> from {@link OperationCancelMonitor#isCancelled()}, the
		 * running will abort, and an {@link ParallelExecutionCancelledException} will be thrown.
		 * 
		 * @param monitor
		 *            Cancellation monitor for the tasks. May be <code>null</code>.
		 * @return <code>this</code>
		 */
		public ParallelRunner setMonitor(OperationCancelMonitor monitor) {
			this.monitor = monitor;
			return this;
		}

		/**
		 * Runs the argument runnables concurrently.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param runnables
		 *            The runnables to run.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public void runRunnables(Runnable... runnables) throws ParallelExecutionException {
			runRunnables(toSupplier(runnables));
		}

		/**
		 * Runs the argument runnables concurrently.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param runnables
		 *            The runnables to run.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public void runRunnables(Iterator<? extends Runnable> runnables) throws ParallelExecutionException {
			runRunnables(toSupplier(runnables));
		}

		/**
		 * Runs the argument runnables concurrently.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param runnables
		 *            The runnables to run.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public void runRunnables(Iterable<? extends Runnable> runnables) throws ParallelExecutionException {
			runRunnables(toSupplier(runnables));
		}

		/**
		 * Runs the argument runnables concurrently.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * <p>
		 * <b>Warning:</b> The argument supplier <b>must</b> return a <code>null</code> result eventually, else an
		 * infinite number of tasks will be started.
		 * 
		 * @param runnables
		 *            The runnables to run.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public void runRunnables(Supplier<? extends Runnable> runnables) throws ParallelExecutionException {
			runContextItems(runnables, Functionals.nullSupplier(), (t, c) -> t.run());
		}

		/**
		 * Runs the specified worker function for the given items.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param items
		 *            The items to run the worker for.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T> void runItems(T[] items, ThrowingConsumer<? super T> worker) throws ParallelExecutionException {
			runItems(toSupplier(items), worker);
		}

		/**
		 * Runs the specified worker function for the given items.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param items
		 *            The items to run the worker for.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T> void runItems(Iterator<? extends T> items, ThrowingConsumer<? super T> worker)
				throws ParallelExecutionException {
			runItems(toSupplier(items), worker);
		}

		/**
		 * Runs the specified worker function for the given items.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param items
		 *            The items to run the worker for.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T> void runItems(Iterable<? extends T> items, ThrowingConsumer<? super T> worker)
				throws ParallelExecutionException {
			runItems(toSupplier(items), worker);
		}

		/**
		 * Runs the specified worker function for the given items.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * <p>
		 * <b>Warning:</b> The argument supplier <b>must</b> return a <code>null</code> result eventually, else an
		 * infinite number of tasks will be started.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param items
		 *            The items to run the worker for.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T> void runItems(Supplier<? extends T> items, ThrowingConsumer<? super T> worker)
				throws ParallelExecutionException {
			runContextItems(items, Functionals.nullSupplier(), (t, c) -> worker.accept(t));
		}

		/**
		 * Runs the specified worker function for the given items and using the created context objects.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param <C>
		 *            The type of the context object.
		 * @param items
		 *            The items to run the worker for.
		 * @param contextsupplier
		 *            The context supplier to create the context object for the workers.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T, C> void runContextItems(T[] items, Supplier<? extends C> contextsupplier,
				ThrowingContextConsumer<? super T, ? super C> worker) throws ParallelExecutionException {
			runContextItems(toSupplier(items), contextsupplier, worker);
		}

		/**
		 * Runs the specified worker function for the given items and using the created context objects.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param <C>
		 *            The type of the context object.
		 * @param items
		 *            The items to run the worker for.
		 * @param contextsupplier
		 *            The context supplier to create the context object for the workers.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T, C> void runContextItems(Iterator<? extends T> items, Supplier<? extends C> contextsupplier,
				ThrowingContextConsumer<? super T, ? super C> worker) throws ParallelExecutionException {
			runContextItems(toSupplier(items), contextsupplier, worker);
		}

		/**
		 * Runs the specified worker function for the given items and using the created context objects.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param <C>
		 *            The type of the context object.
		 * @param items
		 *            The items to run the worker for.
		 * @param contextsupplier
		 *            The context supplier to create the context object for the workers.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T, C> void runContextItems(Iterable<? extends T> items, Supplier<? extends C> contextsupplier,
				ThrowingContextConsumer<? super T, ? super C> worker) throws ParallelExecutionException {
			runContextItems(toSupplier(items), contextsupplier, worker);
		}

		/**
		 * Runs the specified worker function for the given items and using the created context objects.
		 * <p>
		 * See {@link ParallelRunner} documentation for more info.
		 * <p>
		 * <b>Warning:</b> The argument supplier <b>must</b> return a <code>null</code> result eventually, else an
		 * infinite number of tasks will be started.
		 * 
		 * @param <T>
		 *            The type of the items.
		 * @param <C>
		 *            The type of the context object.
		 * @param items
		 *            The items to run the worker for.
		 * @param contextsupplier
		 *            The context supplier to create the context object for the workers.
		 * @param worker
		 *            The worker function to run for each item.
		 * @throws ParallelExecutionException
		 *             If the execution is cancelled, or the runnables throw an exception.
		 */
		public <T, C> void runContextItems(Supplier<? extends T> items, Supplier<? extends C> contextsupplier,
				ThrowingContextConsumer<? super T, ? super C> worker) throws ParallelExecutionException {
			runParallel(threadGroup, items, contextsupplier, threadCount, worker, namePrefix, monitor);
		}
	}

	/**
	 * Starts a new thread that executes the argument runnable.
	 * 
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 */
	public static Thread startThread(Runnable runnable) throws NullPointerException {
		return startThread(null, null, runnable);
	}

	/**
	 * Starts a new thread with the given name that executes the argument runnable.
	 * 
	 * @param name
	 *            The name of the new thread.
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 */
	public static Thread startThread(String name, Runnable runnable) throws NullPointerException {
		return startThread(null, name, runnable);
	}

	/**
	 * Starts a new thread on the given thread group that executes the argument runnable.
	 * 
	 * @param group
	 *            The thread group to start the thread on.
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 */
	public static Thread startThread(ThreadGroup group, Runnable runnable) throws NullPointerException {
		String name = "User thread: " + runnable;
		return startThread(group, name, runnable);
	}

	/**
	 * Starts a new thread on the given thread group with the specified name that executes the argument runnable.
	 * 
	 * @param group
	 *            The thread group to start the thread on.
	 * @param name
	 *            The name of the new thread.
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 */
	public static Thread startThread(ThreadGroup group, String name, Runnable runnable) throws NullPointerException {
		Objects.requireNonNull(runnable, "runnable");
		Thread result;
		if (ObjectUtils.isNullOrEmpty(name)) {
			result = new Thread(group, runnable);
		} else {
			result = new Thread(group, runnable, name);
		}
		result.start();
		return result;
	}

	/**
	 * Starts a new daemon thread with the given name that executes the argument runnable.
	 * 
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 * @see Thread#setDaemon(boolean)
	 */
	public static Thread startDaemonThread(Runnable runnable) throws NullPointerException {
		return startDaemonThread((ThreadGroup) null, runnable);
	}

	/**
	 * Starts a new daemon thread with the given name that executes the argument runnable.
	 * 
	 * @param name
	 *            The name of the new thread.
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 * @see Thread#setDaemon(boolean)
	 */
	public static Thread startDaemonThread(String name, Runnable runnable) throws NullPointerException {
		return startDaemonThread(null, name, runnable);
	}

	/**
	 * Starts a new daemon thread on the given thread group that executes the argument runnable.
	 * 
	 * @param group
	 *            The thread group to start the thread on.
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 * @see Thread#setDaemon(boolean)
	 */
	public static Thread startDaemonThread(ThreadGroup group, Runnable runnable) throws NullPointerException {
		String name = "User daemon thread: " + runnable;
		return startDaemonThread(group, name, runnable);
	}

	/**
	 * Starts a new daemon thread on the given thread group with the specified name that executes the argument runnable.
	 * 
	 * @param group
	 *            The thread group to start the thread on.
	 * @param name
	 *            The name of the new thread.
	 * @param runnable
	 *            The runnable.
	 * @return The started thread.
	 * @throws NullPointerException
	 *             If the runnable is <code>null</code>.
	 * @see Thread#setDaemon(boolean)
	 */
	public static Thread startDaemonThread(ThreadGroup group, String name, Runnable runnable)
			throws NullPointerException {
		Objects.requireNonNull(runnable, "runnable");
		Thread result;
		if (ObjectUtils.isNullOrEmpty(name)) {
			result = new Thread(group, runnable);
		} else {
			result = new Thread(group, runnable, name);
		}
		result.setDaemon(true);
		result.start();
		return result;
	}

	/**
	 * Joins the argument thread.
	 * <p>
	 * This is a single {@link Thread} parameter overload of {@link #joinThreads(Thread...)}.
	 * 
	 * @param thread
	 *            The thread to join. May be <code>null</code>, in which case this function call is a no-op.
	 * @throws InterruptedException
	 *             If the current thread was interrupted while joining.
	 * @throws IllegalThreadStateException
	 *             If the argument is the current thread. (I.e. the thread tries to join itself)
	 * @since saker.util 0.8.4
	 */
	public static void joinThreads(Thread thread) throws InterruptedException, IllegalThreadStateException {
		if (thread == null) {
			return;
		}
		final Thread ct = Thread.currentThread();
		if (thread == ct) {
			throw new IllegalThreadStateException("Trying to join current thread.");
		}
		thread.join();
	}

	/**
	 * Joins the argument threads.
	 * <p>
	 * If this method finishes successfully, all the arguments thread will be in a finished state.
	 * 
	 * @param threads
	 *            The threads to join. May be <code>null</code>, in which case this function call is a no-op.
	 * @throws InterruptedException
	 *             If the current thread was interrupted while joining.
	 * @throws IllegalThreadStateException
	 *             If the argument contains the current thread. (I.e. the thread tries to join itself)
	 */
	public static void joinThreads(Thread... threads) throws InterruptedException, IllegalThreadStateException {
		if (threads == null) {
			return;
		}
		joinThreadsImpl(new ArrayIterator<>(threads));
	}

	/**
	 * Joins the argument threads.
	 * <p>
	 * If this method finishes successfully, all the arguments thread will be in a finished state.
	 * 
	 * @param threads
	 *            The threads to join. May be <code>null</code>, in which case this function call is a no-op.
	 * @throws InterruptedException
	 *             If the current thread was interrupted while joining.
	 * @throws IllegalThreadStateException
	 *             If the argument contains the current thread. (I.e. the thread tries to join itself)
	 */
	public static void joinThreads(Iterable<? extends Thread> threads)
			throws InterruptedException, IllegalThreadStateException {
		if (threads == null) {
			return;
		}
		Objects.requireNonNull(threads, "threads");
		joinThreadsImpl(threads.iterator());
	}

	/**
	 * Joins the argument thread non-interruptibly.
	 * <p>
	 * If the current thread is interrupted while joining, the interrupt flag is stored, and the joining will continue.
	 * If the thread was interrupted, it will be reinterrupted at the end of the method.
	 * <p>
	 * This is a single {@link Thread} parameter overload of {@link #joinThreadsNonInterruptible(Thread...)}.
	 * 
	 * @param thread
	 *            The thread to join. May be <code>null</code>, in which case this function call is a no-op.
	 * @throws IllegalThreadStateException
	 *             If the argument is the current thread. (I.e. the thread tries to join itself)
	 * @since saker.util 0.8.4
	 */
	public static void joinThreadsNonInterruptible(Thread thread) throws IllegalThreadStateException {
		if (thread == null) {
			return;
		}

		final Thread ct = Thread.currentThread();
		if (thread == ct) {
			throw new IllegalThreadStateException("Trying to join current thread.");
		}
		boolean interrupted = false;
		while (true) {
			try {
				thread.join();
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			//if we were interrupted, set the flag again
			ct.interrupt();
		}
	}

	/**
	 * Joins the argument threads non-interruptibly.
	 * <p>
	 * If the current thread is interrupted while joining, the interrupt flag is stored, and the joining will continue.
	 * If the thread was interrupted, it will be reinterrupted at the end of the method.
	 * <p>
	 * If this method finishes successfully, all the arguments thread will be in a finished state.
	 * 
	 * @param threads
	 *            The threads to join. May be <code>null</code>, in which case this function call is a no-op.
	 * @throws IllegalThreadStateException
	 *             If the argument contains the current thread. (I.e. the thread tries to join itself)
	 */
	public static void joinThreadsNonInterruptible(Thread... threads) throws IllegalThreadStateException {
		if (threads == null) {
			return;
		}
		joinThreadsNonInterruptibleImpl(new ArrayIterator<>(threads));
	}

	/**
	 * Joins the argument threads non-interruptibly.
	 * <p>
	 * If the current thread is interrupted while joining, the interrupt flag is stored, and the joining will continue.
	 * If the thread was interrupted, it will be reinterrupted at the end of the method.
	 * <p>
	 * If this method finishes successfully, all the arguments thread will be in a finished state.
	 * 
	 * @param threads
	 *            The threads to join. May be <code>null</code>, in which case this function call is a no-op.
	 * @throws IllegalThreadStateException
	 *             If the argument contains the current thread. (I.e. the thread tries to join itself)
	 */
	public static void joinThreadsNonInterruptible(Iterable<? extends Thread> threads)
			throws IllegalThreadStateException {
		if (threads == null) {
			return;
		}
		joinThreadsNonInterruptibleImpl(threads.iterator());
	}

	/**
	 * Interrupts the argument thread if non-<code>null</code>.
	 * 
	 * @param t
	 *            The thread to interrupt.
	 */
	public static void interruptThread(Thread t) {
		if (t != null) {
			t.interrupt();
		}
	}

	/**
	 * Checks if the given thread has the argument thread group as any of its parent.
	 * <p>
	 * If any of the arguments are <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param t
	 *            The thread.
	 * @param group
	 *            The thread group.
	 * @return <code>true</code> if the thread exists in the hierarchy of the given thread group.
	 */
	public static boolean hasParentThreadGroup(Thread t, ThreadGroup group) {
		if (t == null || group == null) {
			return false;
		}
		return group.parentOf(t.getThreadGroup());
	}

	/**
	 * Gets the root (top-level) thread group.
	 * <p>
	 * The top-level thread group is the one which has no parent.
	 * 
	 * @return The top-level thread group;
	 * @see ThreadGroup#getParent()
	 */
	public static ThreadGroup getTopLevelThreadGroup() {
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		while (true) {
			ThreadGroup parent = tg.getParent();
			if (parent == null) {
				return tg;
			}
			tg = parent;
		}
	}

	/**
	 * Sets the default thread factor that newly created concurrent runners should use.
	 * <p>
	 * This method used to set an {@linkplain InheritableThreadLocal inheritable thread local} variable, whose value
	 * would be returned by {@link #getDefaultThreadFactor()}. This feature is deprecated and that method returns a
	 * fixed value based on the available processor count.
	 * 
	 * @deprecated This method doesn't do anything anymore. Not recommended to use, keep track of your desired thread
	 *                 factor, and pass it to the appropriate configuration parameters instead of using an inheritable
	 *                 thread local configuration.
	 * @param threadfactor
	 *            The thread factor to use.
	 */
	@Deprecated
	public static void setInheritableDefaultThreadFactor(int threadfactor) {
		//no-op
	}

	/**
	 * Creates a new parallel runner builder.
	 * <p>
	 * Parallel runners can be used to run specific tasks concurrently, without dealing with thread pools.
	 * <p>
	 * See {@link ParallelRunner} for more information.
	 * 
	 * @return The parallel runner.
	 */
	public static ParallelRunner parallelRunner() {
		return new ParallelRunner();
	}

	/**
	 * Runs the argument runnables concurrently.
	 * <p>
	 * See {@link ParallelRunner} for more information.
	 * 
	 * @param runnables
	 *            The runnables.
	 */
	public static void runParallelRunnables(Runnable... runnables) {
		runParallelContextItems(ImmutableUtils.asUnmodifiableArrayList(runnables), Functionals.nullSupplier(),
				(t, c) -> t.run());
	}

	/**
	 * Runs the argument runnables concurrently.
	 * <p>
	 * See {@link ParallelRunner} for more information.
	 * 
	 * @param runnables
	 *            The runnables.
	 */
	public static void runParallelRunnables(Iterable<? extends Runnable> runnables) {
		runParallelContextItems(runnables, Functionals.nullSupplier(), (t, c) -> t.run());
	}

	/**
	 * Runs the given worker for the specified items concurrently.
	 * <p>
	 * See {@link ParallelRunner} for more information.
	 *
	 * @param <T>
	 *            The type of the items.
	 * @param items
	 *            The items to run the worker concurrently.
	 * @param worker
	 *            The worker to execute for each item.
	 */
	public static <T> void runParallelItems(Iterable<? extends T> items, ThrowingConsumer<? super T> worker) {
		runParallelContextItems(items, Functionals.nullSupplier(), (t, c) -> worker.accept(t));
	}

	/**
	 * Runs the given worker for the specified items and context object supplier the concurrently.
	 * <p>
	 * See {@link ParallelRunner} for more information.
	 * 
	 * @param <T>
	 *            The type of the items.
	 * @param <C>
	 *            The type of the context object.
	 * @param items
	 *            The items to run the worker concurrently.
	 * @param contextsupplier
	 *            The context supplier to create the context object for the workers.
	 * @param worker
	 *            The worker that executes for each item.
	 */
	public static <T, C> void runParallelContextItems(Iterable<? extends T> items,
			Supplier<? extends C> contextsupplier, ThrowingContextConsumer<? super T, ? super C> worker) {
		runParallel(DefaultNamePrefixThreadFactory.INSTANCE, toSupplier(items), contextsupplier, -1, worker, null);
	}

	/**
	 * Gets a work pool that executes any task offered to it on the caller thread, at the moment it was offered.
	 * <p>
	 * The returned work pool will execute offered task immediately when called.
	 * 
	 * @return The work pool.
	 */
	public static ThreadWorkPool newDirectWorkPool() {
		return new DirectWorkPool();
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool() {
		return newFixedWorkPool(getDefaultThreadFactor());
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(String nameprefix) {
		return newFixedWorkPool(getDefaultThreadFactor(), nameprefix);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(int threadCount) {
		return newFixedWorkPool(threadCount, getDefaultMonitor());
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group) {
		return newFixedWorkPool(group, getDefaultThreadFactor());
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group, String nameprefix) {
		return newFixedWorkPool(group, getDefaultThreadFactor(), getDefaultMonitor(), nameprefix);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group, int threadCount) {
		return newFixedWorkPool(group, threadCount, getDefaultMonitor());
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(int threadCount, String nameprefix) {
		return newFixedWorkPool(threadCount, getDefaultMonitor(), nameprefix);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param daemon
	 *            Boolean indicating whether the created threads in the work pool should be
	 *            {@linkplain Thread#setDaemon(boolean) daemon}.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(int threadCount, String nameprefix, boolean daemon) {
		return newFixedWorkPool(null, threadCount, getDefaultMonitor(), nameprefix, daemon);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(int threadCount, OperationCancelMonitor monitor) {
		return newFixedWorkPool((ThreadFactory) null, threadCount, monitor);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(int threadCount, OperationCancelMonitor monitor, String nameprefix) {
		return newFixedWorkPool(null, threadCount, monitor, nameprefix);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group, int threadCount, OperationCancelMonitor monitor) {
		return newFixedWorkPool(group, threadCount, monitor, DEFAULT_NAME_PREFIX);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group, int threadCount, OperationCancelMonitor monitor,
			String nameprefix) {
		Thread currentthread = Thread.currentThread();
		if (group == null) {
			//set the thread group to the current, so offered tasks doesn't get started on the offering thread groups, but on this one
			group = currentthread.getThreadGroup();
		}
		return newFixedWorkPool(group, threadCount, monitor, nameprefix, currentthread.isDaemon());
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param daemon
	 *            Boolean indicating whether the created threads in the work pool should be
	 *            {@linkplain Thread#setDaemon(boolean) daemon}.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group, int threadCount, String nameprefix,
			boolean daemon) {
		return newFixedWorkPool(group, threadCount, getDefaultMonitor(), nameprefix, daemon);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @param daemon
	 *            Boolean indicating whether the created threads in the work pool should be
	 *            {@linkplain Thread#setDaemon(boolean) daemon}.
	 * @return The work pool.
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadGroup group, int threadCount, OperationCancelMonitor monitor,
			String nameprefix, boolean daemon) {
		if (threadCount < 0) {
			threadCount = getDefaultThreadFactor();
		}
		if (monitor == null) {
			monitor = getDefaultMonitor();
		}
		if (nameprefix == null) {
			nameprefix = DEFAULT_NAME_PREFIX;
		}
		if (group == null) {
			//set the thread group to the current, so offered tasks doesn't get started on the offering thread groups, but on this one
			group = Thread.currentThread().getThreadGroup();
		}
		return new FixedWorkPool(group, threadCount, monitor, nameprefix, daemon);
	}

	/**
	 * Creates a new work pool that uses a fixed number of threads for task execution.
	 * <p>
	 * The threads are lazily started when new tasks are offered to the thread pool.
	 * 
	 * @param threadfactory
	 *            The thread factory to use for creating new threads. May be <code>null</code> in which case the new
	 *            threads are created and their {@linkplain Thread#setDaemon(boolean) daemon flag} and
	 *            {@linkplain Thread#getThreadGroup() thread group} is set based on the thread that creates the pool.
	 * @param threadCount
	 *            The maximum number of threads that the thread pool spawns. Negative or 0 means the
	 *            {@linkplain #setInheritableDefaultThreadFactor(int) default value}.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The work pool.
	 * @since saker.util 0.8.4
	 */
	public static ThreadWorkPool newFixedWorkPool(ThreadFactory threadfactory, int threadCount,
			OperationCancelMonitor monitor) {
		if (threadCount < 0) {
			threadCount = getDefaultThreadFactor();
		}
		if (monitor == null) {
			monitor = getDefaultMonitor();
		}
		if (threadfactory == null) {
			threadfactory = createDefaultThreadFactoryForkWorkPools();
		}
		return new FixedWorkPool(threadfactory, threadCount, monitor);
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @return The created work pool.
	 */
	public static ThreadWorkPool newDynamicWorkPool() {
		return newDynamicWorkPool(null, DEFAULT_NAME_PREFIX, getDefaultMonitor());
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @return The created work pool.
	 */
	public static ThreadWorkPool newDynamicWorkPool(String nameprefix) {
		return newDynamicWorkPool(null, nameprefix, getDefaultMonitor());
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @return The created work pool.
	 */
	public static ThreadWorkPool newDynamicWorkPool(ThreadGroup group) {
		return newDynamicWorkPool(group, DEFAULT_NAME_PREFIX, getDefaultMonitor());
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @return The created work pool.
	 */
	public static ThreadWorkPool newDynamicWorkPool(ThreadGroup group, String nameprefix) {
		return newDynamicWorkPool(group, nameprefix, getDefaultMonitor());
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The created work pool.
	 */
	public static ThreadWorkPool newDynamicWorkPool(ThreadGroup group, String nameprefix,
			OperationCancelMonitor monitor) {
		Thread currentthread = Thread.currentThread();
		if (group == null) {
			//set the thread group to the current, so offered tasks doesn't get started on the offering thread groups, but on this one
			group = currentthread.getThreadGroup();
		}
		return newDynamicWorkPool(group, nameprefix, monitor, currentthread.isDaemon());
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @param group
	 *            The thread group for the created threads. All created threads will be instantiated with the given
	 *            thread group as its parent.
	 * @param nameprefix
	 *            The name prefix to use for each created thread. Recommended format is <code>"Something-"</code>. A
	 *            thread number identifier will be appended to it by the work pool.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @param daemon
	 *            Boolean indicating whether the created threads in the work pool should be
	 *            {@linkplain Thread#setDaemon(boolean) daemon}.
	 * @return The created work pool.
	 */
	public static ThreadWorkPool newDynamicWorkPool(ThreadGroup group, String nameprefix,
			OperationCancelMonitor monitor, boolean daemon) {
		if (monitor == null) {
			monitor = getDefaultMonitor();
		}
		if (nameprefix == null) {
			nameprefix = DEFAULT_NAME_PREFIX;
		}
		if (group == null) {
			//set the thread group to the current, so offered tasks doesn't get started on the offering thread groups, but on this one
			group = Thread.currentThread().getThreadGroup();
		}
		return new DynamicWorkPool(group, nameprefix, daemon, monitor);
	}

	/**
	 * Creates a new work pool that dynamically creates new threads when new tasks are posted.
	 * <p>
	 * The returned work pool will cache threads for some time, and dynamically allocate new ones if necessary. It will
	 * also exit threads if they've not been used for some time.
	 * 
	 * @param threadfactory
	 *            The thread factory to use for creating new threads. May be <code>null</code> in which case the new
	 *            threads are created and their {@linkplain Thread#setDaemon(boolean) daemon flag} and
	 *            {@linkplain Thread#getThreadGroup() thread group} is set based on the thread that creates the pool.
	 * @param monitor
	 *            Cancellation monitor for the tasks. May be <code>null</code>.
	 * @return The created work pool.
	 * @since saker.util 0.8.4
	 */
	public static ThreadWorkPool newDynamicWorkPool(ThreadFactory threadfactory, OperationCancelMonitor monitor) {
		if (monitor == null) {
			monitor = getDefaultMonitor();
		}
		if (threadfactory == null) {
			threadfactory = createDefaultThreadFactoryForkWorkPools();
		}
		return new DynamicWorkPool(threadfactory, monitor);
	}

	/**
	 * Writes the stack traces of all threads in the JVM that the predicate allows.
	 * <p>
	 * This method should be used only for informational purposes.
	 * 
	 * @param ps
	 *            The print stream to write the stack traces to.
	 * @param predicate
	 *            The predicate to test whether a thread stack traces should be written.
	 * @return The number of threads that have been written to the output.
	 * @throws NullPointerException
	 *             If the print stream is <code>null</code>.
	 */
	public static int dumpAllThreadStackTraces(PrintStream ps, Predicate<? super Thread> predicate)
			throws NullPointerException {
		if (predicate == null) {
			predicate = Functionals.alwaysPredicate();
		}
		int c = 0;
		for (Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
			Thread thread = entry.getKey();
			if (!predicate.test(thread)) {
				continue;
			}
			++c;
			ps.println(thread);
			for (StackTraceElement ste : entry.getValue()) {
				ps.println("    " + ste);
			}
		}
		return c;
	}

	/**
	 * Writes the stack traces of all threads in the JVM to the given print stream.
	 * <p>
	 * This method should be used only for informational purposes.
	 * 
	 * @param ps
	 *            The print stream to write the stack traces to.
	 * @return The number of threads that have been written to the output.
	 * @throws NullPointerException
	 *             If the print stream is <code>null</code>.
	 */
	public static int dumpAllThreadStackTraces(PrintStream ps) throws NullPointerException {
		return dumpAllThreadStackTraces(ps, Functionals.alwaysPredicate());
	}

	/**
	 * Writes the stack traces of all threads in the JVM which have the given thread group as any of its parent.
	 * <p>
	 * This method should be used only for informational purposes.
	 * 
	 * @param ps
	 *            The print stream to write the stack traces to.
	 * @param threadgroup
	 *            The thread group for which to list the threads.
	 * @return The number of threads that have been written to the output.
	 * @throws NullPointerException
	 *             If the print stream is <code>null</code>.
	 * @see {@link #hasParentThreadGroup(Thread, ThreadGroup)}
	 */
	public static int dumpThreadGroupStackTraces(PrintStream ps, ThreadGroup threadgroup) throws NullPointerException {
		ps.println(threadgroup + ": ");
		return dumpAllThreadStackTraces(ps, t -> hasParentThreadGroup(t, threadgroup));
	}

	/**
	 * Creates a new non-reentrant {@link Lock} that can only be exclusively held by a single thread.
	 * <p>
	 * The lock will throw an {@link IllegalThreadStateException} in case reentrant locking is attempted.
	 * 
	 * @return The new exclusive lock.
	 * @since saker.util 0.8.4
	 */
	public static Lock newExclusiveLock() {
		return new ExclusiveLock();
	}

	private static final OperationCancelMonitor MONITOR_INSTANCE_NEVER_CANCELLED = () -> false;

	private static OperationCancelMonitor getDefaultMonitor() {
		return MONITOR_INSTANCE_NEVER_CANCELLED;
	}

	//set the min value to 2, so there is at least some concurrency
	private static final int DEFAULT_THREAD_FACTOR_INITIAL_VALUE = Math
			.max(Runtime.getRuntime().availableProcessors() * 3 / 2, 2);

	/**
	 * Gets the default thread concurrency factor.
	 * <p>
	 * This method used to return the value of an {@linkplain InheritableThreadLocal inheritable thread local}, with an
	 * initial value of <code>Math.max(Runtime.getRuntime().availableProcessors() * 3 / 2, 2)</code>. Now it returns
	 * this fixed initial value.
	 * 
	 * @deprecated Not recommended to use, keep track of your desired thread factor, and pass it to the appropriate
	 *                 configuration parameters instead of using an inheritable thread local configuration.
	 * @return The thread factor.
	 * @see #setInheritableDefaultThreadFactor(int)
	 * @since saker.util 0.8.2
	 */
	@Deprecated
	public static int getDefaultThreadFactor() {
		return DEFAULT_THREAD_FACTOR_INITIAL_VALUE;
	}

	private static <T> Supplier<T> toSupplier(T[] items) {
		if (items == null) {
			return null;
		}
		return toSupplier(new ArrayIterator<>(items));
	}

	private static <T> Supplier<T> toSupplier(Iterable<T> it) {
		if (it == null) {
			return null;
		}
		return toSupplier(it.iterator());
	}

	private static <T> Supplier<T> toSupplier(Iterator<T> it) {
		if (it == null) {
			return null;
		}
		return new IteratorDelegateSupplier<T>(it);
	}

	private static void throwOnException(ExceptionHoldingSupplier<?> parallelit) throws ParallelExecutionException {
		ParallelExecutionException exc = parallelit.constructException();
		if (exc != null) {
			throw exc;
		}
	}

	private static <T, C> void callWorker(ThrowingContextConsumer<? super T, ? super C> worker, T workobj, C contextobj,
			ExceptionHoldingSupplier<?> ehit, OperationCancelMonitor monitor) {
		try {
			if (workobj == SUPPLIER_NULL_ELEMENT_PLACEHOLDER) {
				workobj = null;
			}
			worker.accept(workobj, contextobj);
		} catch (ParallelExecutionAbortedException e) {
			ehit.aborted(e);
		} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
				| Exception e) {
			ehit.failed(e);
		} catch (Throwable e) {
			//in any other case, for more fatal exceptions
			ehit.failed(e);
			throw e;
		} finally {
			ehit.done();
		}
		if (monitor.isCancelled()) {
			ehit.cancelled("Cancelled by monitor.");
		}
	}

	private static <C, T> void runParallel(ThreadGroup group, Supplier<? extends T> it,
			Supplier<? extends C> contextsupplier, int threadcount,
			ThrowingContextConsumer<? super T, ? super C> worker, String nameprefix, OperationCancelMonitor monitor) {
		ThreadFactory threadfactory;
		if (nameprefix == null) {
			threadfactory = new GroupDefaultNamePrefixThreadFactory(group);
		} else {
			threadfactory = new GroupNamePrefixThreadFactory(group, nameprefix);
		}
		runParallel(threadfactory, it, contextsupplier, threadcount, worker, monitor);
	}

	private static <C, T> void runParallel(ThreadFactory threadfactory, Supplier<? extends T> it,
			Supplier<? extends C> contextsupplier, int threadcount,
			ThrowingContextConsumer<? super T, ? super C> worker, OperationCancelMonitor monitor) {
		if (it == null) {
			return;
		}
		SimpleExceptionHoldingSupplier<T> ehit = new SimpleExceptionHoldingSupplier<>(it);

		T workobj = ehit.get();
		if (workobj == null) {
			return;
		}

		if (contextsupplier == null) {
			contextsupplier = Functionals.nullSupplier();
		}
		if (monitor == null) {
			monitor = getDefaultMonitor();
		}
		if (threadcount < 0) {
			threadcount = getDefaultThreadFactor();
		}
		if (Thread.currentThread().isInterrupted()) {
			throw new ParallelExecutionCancelledException("Interrupted.");
		}

		if (threadcount <= 1) {
			C cc = contextsupplier.get();
			callWorker(worker, workobj, cc, ehit, monitor);
			while (!ehit.isAborted() && (workobj = ehit.get()) != null) {
				callWorker(worker, workobj, cc, ehit, monitor);
			}
			throwOnException(ehit);
			return;
		}
		T secondworkobj = ehit.get();
		if (secondworkobj == null) {
			C cc = contextsupplier.get();
			//only single work object, run it without creating a thread
			callWorker(worker, workobj, cc, ehit, monitor);
			throwOnException(ehit);
			return;
		}
		//more than one item, and more than 1 thread requested

		List<Thread> threads = new ArrayList<>(threadcount);

		//unroll two loops
		Thread t1 = threadfactory
				.newThread(new WorkerThreadRunnable<>(ehit, workobj, worker, contextsupplier, monitor));
		threads.add(t1);
		t1.start();
		Thread t2 = threadfactory
				.newThread(new WorkerThreadRunnable<>(ehit, secondworkobj, worker, contextsupplier, monitor));
		threads.add(t2);
		t2.start();
		for (int i = 2; i < threadcount; i++) {
			T nextobj = ehit.get();
			if (nextobj == null || ehit.isAborted()) {
				break;
			}
			Thread thread = threadfactory
					.newThread(new WorkerThreadRunnable<>(ehit, nextobj, worker, contextsupplier, monitor));
			threads.add(thread);
			thread.start();
		}

		joinParallelThreads(threads, ehit);
		throwOnException(ehit);
	}

	private static void joinParallelThreads(List<? extends Thread> threads, ExceptionHoldingSupplier<?> ehit) {
		joinParallelThreads(threads::get, threads.size(), ehit);
	}

	private static <R extends Reference<? extends Thread>> void joinClearParallelThreadsNonInterruptibleRef(
			ConcurrentPrependAccumulator<R> threads) {
		final Thread ct = Thread.currentThread();
		boolean interrupted = false;
		for (R tref; (tref = threads.take()) != null;) {
			Thread t = tref.get();
			if (t == ct) {
				//put back to join later
				threads.add(tref);
				throw new IllegalThreadStateException("Trying to join current thread.");
			}
			while (true) {
				try {
					t.join();
					break;
				} catch (InterruptedException e) {
					interrupted = true;
					// join this thread again, we were just signaled interruption
				}
			}
		}
		if (interrupted) {
			//if we were interrupted, set the flag again
			ct.interrupt();
		}
	}

	private static <R extends Reference<? extends Thread>> void joinClearParallelThreadsRefInterruptible(
			ConcurrentPrependAccumulator<R> threads) throws InterruptedException {
		final Thread ct = Thread.currentThread();
		for (R tref; (tref = threads.take()) != null;) {
			Thread t = tref.get();
			if (t == null) {
				continue;
			}
			if (t == ct) {
				//put back to join later
				threads.add(tref);
				throw new IllegalThreadStateException("Trying to join current thread.");
			}
			try {
				t.join();
			} catch (InterruptedException e) {
				//put back to join later
				threads.add(tref);
				throw e;
			}
		}
	}

	private static <T, R extends Reference<? extends Thread>> void joinClearParallelThreadsRef(
			ConcurrentPrependAccumulator<R> threads, ExceptionHoldingSupplier<T> ehit) {
		final Thread ct = Thread.currentThread();
		boolean interrupted = false;
		for (R tref; (tref = threads.take()) != null;) {
			Thread t = tref.get();
			if (t == null) {
				continue;
			}
			if (t == ct) {
				//put back to join later
				threads.add(tref);
				throw new IllegalThreadStateException("Trying to join current thread.");
			}
			while (true) {
				try {
					t.join();
					break;
				} catch (InterruptedException e) {
					// we got interrupted during joining
					// shut down the remaining threads
					if (!interrupted) {
						t.interrupt();
						for (Iterator<? extends R> it = threads.iterator(); it.hasNext();) {
							R tref2 = it.next();
							Thread t2 = tref2.get();
							if (t2 != null) {
								t2.interrupt();
							}
						}
						interrupted = true;
					}
				}
				// join this thread again, we were just signaled interruption
			}
		}
		if (interrupted) {
			//if we were interrupted, set the flag again
			ct.interrupt();
			if (!ehit.isAllTasksProcessed()) {
				ehit.cancelled("Interrupted.");
			}
		}
	}

	private static void joinParallelThreads(Function<Integer, ? extends Thread> threadindexer, int count,
			ExceptionHoldingSupplier<?> ehit) {
		final Thread ct = Thread.currentThread();
		boolean interrupted = false;
		for (int i = 0; i < count; i++) {
			Thread t = threadindexer.apply(i);
			if (t == ct) {
				throw new IllegalThreadStateException("Trying to join current thread.");
			}
			while (true) {
				try {
					t.join();
					break;
				} catch (InterruptedException e) {
					// we got interrupted during joining
					// shut down the remaining threads
					if (!interrupted) {
						t.interrupt();
						for (int j = i + 1; j < count; j++) {
							threadindexer.apply(j).interrupt();
						}
						interrupted = true;
					}
				}
				// join this thread again, we were just signaled interruption
			}
		}
		if (interrupted) {
			//if we were interrupted, set the flag again
			ct.interrupt();
			if (!ehit.isAllTasksProcessed()) {
				ehit.cancelled("Interrupted.");
			}
		}
	}

	private static void joinThreadsImpl(Iterator<? extends Thread> it) throws InterruptedException {
		Objects.requireNonNull(it, "iterator");
		if (!it.hasNext()) {
			return;
		}
		final Thread ct = Thread.currentThread();
		do {
			Thread t = it.next();
			if (t == null) {
				continue;
			}
			if (t == ct) {
				throw new IllegalThreadStateException("Trying to join current thread.");
			}
			t.join();
		} while (it.hasNext());
	}

	private static void joinThreadsNonInterruptibleImpl(Iterator<? extends Thread> it)
			throws IllegalThreadStateException {
		Objects.requireNonNull(it, "iterator");
		if (!it.hasNext()) {
			return;
		}
		final Thread ct = Thread.currentThread();
		boolean interrupted = false;
		do {
			Thread t = it.next();
			if (t == null) {
				continue;
			}
			if (t == ct) {
				throw new IllegalThreadStateException("Trying to join current thread.");
			}
			while (true) {
				try {
					t.join();
					break;
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		} while (it.hasNext());
		if (interrupted) {
			//if we were interrupted, set the flag again
			ct.interrupt();
		}
	}

	private static ThreadFactory createDefaultThreadFactoryForkWorkPools() {
		Thread currentthread = Thread.currentThread();
		return new GroupNamePrefixDaemonThreadFactory(currentthread.getThreadGroup(), DEFAULT_NAME_PREFIX,
				currentthread.isDaemon());
	}

	private static class DirectWorkPool implements ThreadWorkPool {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ThreadUtils.DirectWorkPool, ConcurrentAppendAccumulator> ARFU_exceptions = AtomicReferenceFieldUpdater
				.newUpdater(ThreadUtils.DirectWorkPool.class, ConcurrentAppendAccumulator.class, "exceptions");
		private volatile ConcurrentAppendAccumulator<Throwable> exceptions;

		@Override
		public void offer(ThrowingRunnable task) {
			Objects.requireNonNull(task, "task");
			try {
				task.run();
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				ConcurrentAppendAccumulator<Throwable> exc = this.exceptions;
				if (exc == null) {
					exc = new ConcurrentAppendAccumulator<>();
					if (!ARFU_exceptions.compareAndSet(this, null, exc)) {
						exc = this.exceptions;
					}
				}
				exc.add(e);
			}
			//any other exceptions are propagated
		}

		@Override
		public void closeInterruptible() throws ParallelExecutionException, InterruptedException {
			throwAnyException();
		}

		@Override
		public void close() throws ParallelExecutionException {
			throwAnyException();
		}

		@Override
		public void exit() {
		}

		@Override
		public void reset() {
			throwAnyException();
		}

		@Override
		public void resetInterruptible() {
			throwAnyException();
		}

		private void throwAnyException() {
			@SuppressWarnings("unchecked")
			ConcurrentAppendAccumulator<Throwable> exc = ARFU_exceptions.getAndSet(this, null);
			if (exc != null) {
				ParallelExecutionFailedException e = new ParallelExecutionFailedException();
				for (Throwable t : exc) {
					e.addSuppressed(t);
				}
				throw e;
			}
		}
	}

	private static class StateNode<T> {
		T item;
		StateNode<T> next;

		public StateNode(T item, StateNode<T> next) {
			this.item = item;
			this.next = next;
		}
	}

	private static class ExceptionState {
		public static final ExceptionState EMPTY = new ExceptionState(null, null, null);

		final StateNode<ParallelExecutionAbortedException> aborts;
		final StateNode<ParallelExecutionCancelledException> cancels;
		final StateNode<Throwable> failures;

		public ExceptionState(StateNode<ParallelExecutionAbortedException> aborts,
				StateNode<ParallelExecutionCancelledException> cancels, StateNode<Throwable> failures) {
			this.aborts = aborts;
			this.cancels = cancels;
			this.failures = failures;
		}

		public ExceptionState abort(ParallelExecutionAbortedException e) {
			return new ExceptionState(new StateNode<>(e, this.aborts), cancels, failures);
		}

		public ExceptionState fail(Throwable e) {
			return new ExceptionState(this.aborts, cancels, new StateNode<>(e, this.failures));
		}

		public ExceptionState cancel(ParallelExecutionCancelledException e) {
			return new ExceptionState(this.aborts, new StateNode<>(e, this.cancels), failures);
		}

		public boolean isEmpty() {
			return this.aborts == null && this.cancels == null && this.failures == null;
		}

	}

	private static final class GroupNamePrefixDaemonThreadFactory implements ThreadFactory {
		private static final AtomicIntegerFieldUpdater<ThreadUtils.GroupNamePrefixDaemonThreadFactory> AIFU_counter = AtomicIntegerFieldUpdater
				.newUpdater(ThreadUtils.GroupNamePrefixDaemonThreadFactory.class, "counter");
		@SuppressWarnings("unused")
		private volatile int counter;

		private final ThreadGroup group;
		private final String namePrefix;
		private final boolean daemon;

		public GroupNamePrefixDaemonThreadFactory(ThreadGroup group, String namePrefix, boolean daemon) {
			this.group = group;
			this.namePrefix = namePrefix;
			this.daemon = daemon;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(group, r, namePrefix + AIFU_counter.incrementAndGet(this));
			thread.setDaemon(daemon);
			return thread;
		}
	}

	private static final class GroupNamePrefixThreadFactory implements ThreadFactory {
		private static final AtomicIntegerFieldUpdater<ThreadUtils.GroupNamePrefixThreadFactory> AIFU_counter = AtomicIntegerFieldUpdater
				.newUpdater(ThreadUtils.GroupNamePrefixThreadFactory.class, "counter");
		@SuppressWarnings("unused")
		private volatile int counter;

		private final ThreadGroup group;
		private final String namePrefix;

		public GroupNamePrefixThreadFactory(ThreadGroup group, String namePrefix) {
			this.group = group;
			this.namePrefix = namePrefix;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(group, r, namePrefix + AIFU_counter.incrementAndGet(this));
			return thread;
		}
	}

	private static final class GroupDefaultNamePrefixThreadFactory implements ThreadFactory {
		private final ThreadGroup group;

		public GroupDefaultNamePrefixThreadFactory(ThreadGroup group) {
			this.group = group;
		}

		@Override
		public Thread newThread(Runnable r) {
			//pass through the default name prefix thread factory so the counter is shared
			return DefaultNamePrefixThreadFactory.createWithGroup(group, r);
		}
	}

	private static final class DefaultNamePrefixThreadFactory implements ThreadFactory {
		private static final AtomicIntegerFieldUpdater<ThreadUtils.DefaultNamePrefixThreadFactory> AIFU_counter = AtomicIntegerFieldUpdater
				.newUpdater(ThreadUtils.DefaultNamePrefixThreadFactory.class, "counter");
		@SuppressWarnings("unused")
		private volatile int counter;

		//a single global instance is fine
		public static final DefaultNamePrefixThreadFactory INSTANCE = new DefaultNamePrefixThreadFactory();

		public DefaultNamePrefixThreadFactory() {
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, DEFAULT_NAME_PREFIX + AIFU_counter.incrementAndGet(this));
			return thread;
		}

		public static Thread createWithGroup(ThreadGroup group, Runnable r) {
			return new Thread(group, r, DEFAULT_NAME_PREFIX + AIFU_counter.incrementAndGet(INSTANCE));
		}
	}

	private static class FixedWorkPool implements ThreadWorkPool {

		private class FixedThreadCountSupplier implements ExceptionHoldingSupplier<ThrowingRunnable> {
			private final ReentrantLock lock = new ReentrantLock();
			private final Condition cond = lock.newCondition();

			@Override
			public ThrowingRunnable get(Thread currentthread) {
				boolean waitadded = false;

				lock.lock();
				try {
					while (true) {
						PoolState s = FixedWorkPool.this.state;
						if (s.task != null) {
							PoolState nstate = s.takeTask(waitadded);
							if (ARFU_state.compareAndSet(FixedWorkPool.this, s, nstate)) {
								return s.task.item;
							}
							//somebody else took the task or state changed
							continue;
						}
						if (!waitadded) {
							PoolState waitnstate = s.addWaitingThread();
							if (!ARFU_state.compareAndSet(FixedWorkPool.this, s, waitnstate)) {
								continue;
							}
							waitadded = true;
							s = waitnstate;
							FixedWorkPool.this.notifyThreadSync();
						}
						//there is not task pending, and we're waiting
						if (s.isCloseExited()) {
							//the thread pool is closed
							PoolState exitedstate = s.waitingThreadExits();
							if (ARFU_state.compareAndSet(FixedWorkPool.this, s, exitedstate)) {
								cond.signalAll();
								FixedWorkPool.this.notifyThreadSync();
								return null;
							}
							//somebody changed the state
							continue;
						}
						try {
							//wait for some external state change
							cond.await();
						} catch (InterruptedException e) {
							cancelled(e);
						}
					}
				} finally {
					lock.unlock();
				}
			}

			@Override
			public boolean isAllTasksProcessed() {
				throw new UnsupportedOperationException();
			}

			public void notifyStateChange() {
				lock.lock();
				try {
					cond.signalAll();
				} finally {
					lock.unlock();
				}
			}

			public void notifyOffer() {
				lock.lock();
				try {
					cond.signal();
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void aborted(ParallelExecutionAbortedException e) {
				ARFU_state.updateAndGet(FixedWorkPool.this, s -> s.abortException(e));
				lock.lock();
				try {
					cond.signalAll();
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void cancelled() {
				cancelException(new ParallelExecutionCancelledException());
			}

			@Override
			public void cancelled(String message) {
				cancelException(new ParallelExecutionCancelledException(message));
			}

			@Override
			public void cancelled(Throwable cause) {
				cancelException(new ParallelExecutionCancelledException(cause));
			}

			private void cancelException(ParallelExecutionCancelledException e) {
				ARFU_state.updateAndGet(FixedWorkPool.this, s -> s.cancelException(e));
			}

			@Override
			public void failed(Throwable e) {
				ARFU_state.updateAndGet(FixedWorkPool.this, s -> s.failException(e));
			}

			@Override
			public boolean isAborted() {
				//return false, so the threads don't exit without syncing
				return false;
			}

			@Override
			public ParallelExecutionException constructException() {
				return FixedWorkPool.this.state.constructException();
			}
		}

		private static final int POOL_STATE_RUNNING = 1;
		private static final int POOL_STATE_CLOSED = 2;

		private static class PoolState {
			private int threadCount;
			private int state = POOL_STATE_RUNNING;
			private int waitingThreadCount;
			private StateNode<ThrowingRunnable> task;
			private ExceptionState exceptionState = ExceptionState.EMPTY;

			public PoolState() {
			}

			public PoolState(PoolState s, int nstate) {
				this.threadCount = s.threadCount;
				this.state = nstate;
				this.waitingThreadCount = s.waitingThreadCount;
				this.task = s.task;
				this.exceptionState = s.exceptionState;
			}

			public boolean hasException() {
				return !exceptionState.isEmpty();
			}

			public ParallelExecutionException constructException() {
				StateNode<Throwable> failures = exceptionState.failures;
				if (failures != null) {
					ParallelExecutionFailedException exc = new ParallelExecutionFailedException();
					while (failures != null) {
						exc.addSuppressed(failures.item);
						failures = failures.next;
					}
					return exc;
				}
				StateNode<ParallelExecutionAbortedException> aborts = exceptionState.aborts;
				if (aborts != null) {
					ParallelExecutionAbortedException exc = new ParallelExecutionAbortedException();
					while (aborts != null) {
						exc.addSuppressed(aborts.item);
						aborts = aborts.next;
					}
					return exc;
				}
				StateNode<ParallelExecutionCancelledException> cancels = exceptionState.cancels;
				if (cancels != null) {
					if (this.task == null) {
						//only throw cancellation exception if there was unrun tasks
						ParallelExecutionCancelledException exc = new ParallelExecutionCancelledException();
						while (cancels != null) {
							exc.addSuppressed(cancels.item);
							cancels = cancels.next;
						}
						return exc;
					}
				}
				return null;
			}

			public PoolState offer(ThrowingRunnable task) {
				if (isCloseExited()) {
					throw new IllegalStateException("Thread pool is closed.");
				}
				PoolState res = new PoolState(this, this.state);
				res.task = new StateNode<>(task, this.task);
				return res;
			}

			public PoolState offerForNewThread() {
				if (isCloseExited()) {
					throw new IllegalStateException("Thread pool is closed.");
				}
				PoolState res = new PoolState(this, this.state);
				++res.threadCount;
				return res;
			}

			public PoolState takeTask(boolean fromwaitingthread) {
				if (this.task == null) {
					throw new AssertionError();
				}
				PoolState res = new PoolState(this, this.state);
				res.task = res.task.next;
				if (fromwaitingthread) {
					--res.waitingThreadCount;
				}
				return res;
			}

			public PoolState close() {
				PoolState res = new PoolState(this, POOL_STATE_CLOSED);
				return res;
			}

			public PoolState reset() {
				switch (this.state) {
					case POOL_STATE_CLOSED: {
						throw new IllegalStateException("Thread pool is already closed.");
					}
					case POOL_STATE_RUNNING: {
						//no state change, the state change to finished needs to be waited externally
						return this;
					}
					default: {
						throw new AssertionError(this.state);
					}
				}
			}

			public PoolState withoutException() {
				PoolState res = new PoolState(this, this.state);
				res.exceptionState = ExceptionState.EMPTY;
				return res;
			}

			public PoolState addWaitingThread() {
				PoolState res = new PoolState(this, this.state);
				++res.waitingThreadCount;
				return res;
			}

			public PoolState waitingThreadExits() {
				PoolState res = new PoolState(this, this.state);
				--res.threadCount;
				--res.waitingThreadCount;
				return res;
			}

			public boolean isCloseExited() {
				return state == POOL_STATE_CLOSED && !isAnyTaskRunning();
			}

			public boolean isAnyTaskRunning() {
				return this.waitingThreadCount != this.threadCount || this.task != null;
			}

			public PoolState failException(Throwable exc) {
				PoolState res = new PoolState(this, this.state);
				res.exceptionState = res.exceptionState.fail(exc);
				return res;
			}

			public PoolState cancelException(ParallelExecutionCancelledException exc) {
				PoolState res = new PoolState(this, this.state);
				res.exceptionState = res.exceptionState.cancel(exc);
				return res;
			}

			public PoolState abortException(ParallelExecutionAbortedException exc) {
				PoolState res = new PoolState(this, this.state);
				res.exceptionState = res.exceptionState.abort(exc);
				return res;
			}

			@Override
			public String toString() {
				return "PoolState [threadCount=" + threadCount + ", state=" + state + ", waitingThreadCount="
						+ waitingThreadCount + "]";
			}
		}

		static final AtomicReferenceFieldUpdater<ThreadUtils.FixedWorkPool, PoolState> ARFU_state = AtomicReferenceFieldUpdater
				.newUpdater(ThreadUtils.FixedWorkPool.class, PoolState.class, "state");
		volatile PoolState state = new PoolState();

		private final int maxThreadCount;
		private final ConcurrentPrependAccumulator<Thread> threads = new ConcurrentPrependAccumulator<>();
		private final FixedThreadCountSupplier parallelSupplier = new FixedThreadCountSupplier();
		private final ThreadFactory threadFactory;
		private final OperationCancelMonitor monitor;

		private final ReentrantLock threadsStateNotifyLock = new ReentrantLock();
		private final Condition threadsStateNotifyCondition = threadsStateNotifyLock.newCondition();

		public FixedWorkPool(ThreadGroup group, int threadCount, OperationCancelMonitor monitor, String nameprefix,
				boolean daemon) {
			this(new GroupNamePrefixDaemonThreadFactory(group, nameprefix, daemon), threadCount, monitor);
		}

		public FixedWorkPool(ThreadFactory threadFactory, int maxThreadCount, OperationCancelMonitor monitor) {
			this.threadFactory = threadFactory;
			this.maxThreadCount = maxThreadCount;
			this.monitor = monitor;
		}

		@Override
		public void offer(ThrowingRunnable task) {
			Objects.requireNonNull(task, "task");
			while (true) {
				PoolState s = this.state;
				PoolState nstate;
				if (s.threadCount >= maxThreadCount || (s.waitingThreadCount > 0 && s.task == null)) {
					//if we cant spawn more threads
					//or
					//there is at least one waiting thread and no task pending
					nstate = s.offer(task);
					if (ARFU_state.compareAndSet(this, s, nstate)) {
						parallelSupplier.notifyOffer();
						return;
					}
				} else {
					nstate = s.offerForNewThread();
					if (ARFU_state.compareAndSet(this, s, nstate)) {
						//spawn a new thread
						Thread workthread = threadFactory.newThread(
								new WorkerThreadRunnable<>(parallelSupplier, task, (t, c) -> t.run(), null, monitor));
						threads.add(workthread);
						workthread.start();
						return;
					}
				}
				//continue
			}
		}

		@Override
		public void exit() {
			//nothing to do
		}

		@Override
		public void close() throws ParallelExecutionException {
			ARFU_state.updateAndGet(this, PoolState::close);
			parallelSupplier.notifyStateChange();

			waitThreadSync();
		}

		@Override
		public void closeInterruptible() throws ParallelExecutionException, InterruptedException {
			ARFU_state.updateAndGet(this, PoolState::close);
			parallelSupplier.notifyStateChange();

			waitThreadSyncInterruptible();
		}

		@Override
		public void reset() {
			ARFU_state.updateAndGet(this, PoolState::reset);
			parallelSupplier.notifyStateChange();

			waitThreadSync();
		}

		@Override
		public void resetInterruptible() throws InterruptedException {
			ARFU_state.updateAndGet(this, PoolState::reset);
			parallelSupplier.notifyStateChange();

			waitThreadSyncInterruptible();
		}

		protected void notifyThreadSync() {
			threadsStateNotifyLock.lock();
			try {
				threadsStateNotifyCondition.signalAll();
			} finally {
				threadsStateNotifyLock.unlock();
			}
		}

		private void waitThreadSync() {
			threadsStateNotifyLock.lock();
			try {
				while (true) {
					PoolState s = this.state;
					if (!s.isAnyTaskRunning()) {
						if (s.hasException()) {
							if (ARFU_state.compareAndSet(this, s, s.withoutException())) {
								ParallelExecutionException exc = s.constructException();
								throw exc;
							}
							//failed to swap the state, retry
							continue;
						}
						//no exception, we can just break out of the waiting loop
						break;
					}
					threadsStateNotifyCondition.awaitUninterruptibly();
				}
			} finally {
				threadsStateNotifyLock.unlock();
			}
		}

		private void waitThreadSyncInterruptible() throws InterruptedException {
			threadsStateNotifyLock.lock();
			try {
				while (true) {
					PoolState s = this.state;
					if (!s.isAnyTaskRunning()) {
						if (s.hasException()) {
							if (ARFU_state.compareAndSet(this, s, s.withoutException())) {
								throw s.constructException();
							}
							//failed to swap the state, retry
							continue;
						}
						//no exception, we can just break out of the waiting loop
						break;
					}
					threadsStateNotifyCondition.await();
				}
			} finally {
				threadsStateNotifyLock.unlock();
			}
		}
	}

	private static class DynamicWorkPool implements ThreadWorkPool {
		private final DynamicSmartSupplier<ThrowingRunnable> supplier = new DynamicSmartSupplier<>();
		private final ConcurrentPrependAccumulator<WeakReference<? extends Thread>> threads = new ConcurrentPrependAccumulator<>();
		private final OperationCancelMonitor monitor;
		private final ThreadFactory threadFactory;

		public DynamicWorkPool(ThreadGroup group, String namePrefix, boolean daemon, OperationCancelMonitor monitor) {
			this(new GroupNamePrefixDaemonThreadFactory(group, namePrefix, daemon), monitor);
		}

		public DynamicWorkPool(ThreadFactory threadFactory, OperationCancelMonitor monitor) {
			this.threadFactory = threadFactory;
			this.monitor = monitor;
		}

		@Override
		public void offer(ThrowingRunnable task) {
			Objects.requireNonNull(task, "task");
			boolean success = supplier.offer(task);
			if (!success) {
				Thread workthread = this.threadFactory
						.newThread(new WorkerThreadRunnable<>(supplier, task, (t, c) -> t.run(), null, monitor));
				workthread.start();
				threads.add(new WeakReference<>(workthread));
			}
		}

		@Override
		public void exit() {
			supplier.finish();
		}

		@Override
		public void close() throws ParallelExecutionException {
			supplier.finish();
			joinClearParallelThreadsRef(threads, supplier);
			throwOnException(supplier);
		}

		@Override
		public void closeInterruptible() throws ParallelExecutionException, InterruptedException {
			supplier.finish();
			joinClearParallelThreadsRefInterruptible(threads);
			throwOnException(supplier);
		}

		@Override
		public void reset() {
			//XXX consider leaving the threads alive when resetting
			supplier.finish();
			joinClearParallelThreadsNonInterruptibleRef(threads);
			throwOnException(supplier);
			supplier.reset();
		}

		@Override
		public void resetInterruptible() throws InterruptedException {
			//XXX consider leaving the threads alive when resetting
			supplier.finish();
			joinClearParallelThreadsRefInterruptible(threads);
			throwOnException(supplier);
			supplier.reset();
		}

	}

	private static final String DEFAULT_NAME_PREFIX = "Worker-";
	private static final Object SUPPLIER_NULL_ELEMENT_PLACEHOLDER = new Object();

	private interface ExceptionHoldingSupplier<T> {
		/**
		 * Signals an user requested abortion (exception or interruption).
		 * 
		 * @param e
		 */
		public void aborted(ParallelExecutionAbortedException e);

		public void cancelled();

		public void cancelled(String message);

		public void cancelled(Throwable cause);

		public void failed(Throwable e);

		public ParallelExecutionException constructException();

		/**
		 * Checks if the thread should exit.
		 * 
		 * @return
		 */
		public boolean isAborted();

		public T get(Thread currentthread);

		public default void done() {
		}

		public boolean isAllTasksProcessed();
	}

	private static class SimpleExceptionHoldingSupplier<T> implements ExceptionHoldingSupplier<T> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ThreadUtils.SimpleExceptionHoldingSupplier, ExceptionState> ARFU_exceptionState = AtomicReferenceFieldUpdater
				.newUpdater(ThreadUtils.SimpleExceptionHoldingSupplier.class, ExceptionState.class, "exceptionState");

		private final Supplier<? extends T> it;
		private volatile boolean allTasksProcessed;
		private volatile ExceptionState exceptionState = ExceptionState.EMPTY;

		public SimpleExceptionHoldingSupplier(Supplier<? extends T> it) {
			this.it = it;
		}

		public T get() {
			if (isAborted()) {
				return null;
			}
			T result = it.get();
			if (result == null) {
				allTasksProcessed = true;
			}
			return result;
		}

		@Override
		public T get(Thread currentthread) {
			return get();
		}

		@Override
		public boolean isAllTasksProcessed() {
			return allTasksProcessed;
		}

		@Override
		public void aborted(ParallelExecutionAbortedException e) {
			while (true) {
				ExceptionState state = this.exceptionState;
				ExceptionState nstate = state.abort(e);
				if (ARFU_exceptionState.compareAndSet(this, state, nstate)) {
					break;
				}
			}
		}

		@Override
		public void cancelled() {
			cancel(new ParallelExecutionCancelledException());
		}

		private void cancel(ParallelExecutionCancelledException e) {
			while (true) {
				ExceptionState state = this.exceptionState;
				ExceptionState nstate = state.cancel(e);
				if (ARFU_exceptionState.compareAndSet(this, state, nstate)) {
					break;
				}
			}
		}

		@Override
		public void cancelled(String message) {
			cancel(new ParallelExecutionCancelledException(message));
		}

		@Override
		public void cancelled(Throwable cause) {
			cancel(new ParallelExecutionCancelledException(cause));
		}

		@Override
		public void failed(Throwable e) {
			while (true) {
				ExceptionState state = this.exceptionState;
				ExceptionState nstate = state.fail(e);
				if (ARFU_exceptionState.compareAndSet(this, state, nstate)) {
					break;
				}
			}
		}

		@Override
		public ParallelExecutionException constructException() {
			ExceptionState state = exceptionState;
			StateNode<Throwable> failures = state.failures;
			if (failures != null) {
				ParallelExecutionFailedException exc = new ParallelExecutionFailedException();
				while (failures != null) {
					exc.addSuppressed(failures.item);
					failures = failures.next;
				}
				return exc;
			}
			StateNode<ParallelExecutionAbortedException> aborts = state.aborts;
			if (aborts != null) {
				ParallelExecutionAbortedException exc = new ParallelExecutionAbortedException();
				while (aborts != null) {
					exc.addSuppressed(aborts.item);
					aborts = aborts.next;
				}
				return exc;
			}
			StateNode<ParallelExecutionCancelledException> cancels = state.cancels;
			if (cancels != null) {
				if (!allTasksProcessed) {
					//only throw cancellation exception if there was unrun tasks
					ParallelExecutionCancelledException exc = new ParallelExecutionCancelledException();
					while (cancels != null) {
						exc.addSuppressed(cancels.item);
						cancels = cancels.next;
					}
					return exc;
				}
			}
			return null;
		}

		@Override
		public boolean isAborted() {
			return !this.exceptionState.isEmpty();
		}

	}

	private static class DynamicSmartSupplier<T> implements ExceptionHoldingSupplier<T> {
		private static class State {
			int offeredCount;
			int doneCount;
			boolean finishIfEmpty;
			ExceptionState exceptionState = ExceptionState.EMPTY;

			public State() {
			}

			public State done(State s) {
				this.offeredCount = s.offeredCount;
				this.doneCount = s.doneCount + 1;
				this.finishIfEmpty = s.finishIfEmpty;
				this.exceptionState = s.exceptionState;
				return this;
			}

			public State offer(State s) {
				this.offeredCount = s.offeredCount + 1;
				this.doneCount = s.doneCount;
				this.finishIfEmpty = s.finishIfEmpty;
				this.exceptionState = s.exceptionState;
				return this;
			}

			public State finish(State s) {
				this.offeredCount = s.offeredCount;
				this.doneCount = s.doneCount;
				this.finishIfEmpty = true;
				this.exceptionState = s.exceptionState;
				return this;
			}

			public State aborted(State s, ParallelExecutionAbortedException e) {
				copyFrom(s);
				this.exceptionState = this.exceptionState.abort(e);
				return this;
			}

			public State cancelled(State s, ParallelExecutionCancelledException e) {
				copyFrom(s);
				this.exceptionState = this.exceptionState.cancel(e);
				return this;
			}

			public State failed(State s, Throwable e) {
				copyFrom(s);
				this.exceptionState = this.exceptionState.fail(e);
				return this;
			}

			public ParallelExecutionException constructException() {
				StateNode<Throwable> failures = exceptionState.failures;
				if (failures != null) {
					ParallelExecutionFailedException exc = new ParallelExecutionFailedException();
					while (failures != null) {
						exc.addSuppressed(failures.item);
						failures = failures.next;
					}
					return exc;
				}
				StateNode<ParallelExecutionAbortedException> aborts = exceptionState.aborts;
				if (aborts != null) {
					ParallelExecutionAbortedException exc = new ParallelExecutionAbortedException();
					while (aborts != null) {
						exc.addSuppressed(aborts.item);
						aborts = aborts.next;
					}
					return exc;
				}
				StateNode<ParallelExecutionCancelledException> cancels = exceptionState.cancels;
				if (cancels != null) {
					if (this.offeredCount != this.doneCount) {
						//only throw cancellation exception if there was unrun tasks
						ParallelExecutionCancelledException exc = new ParallelExecutionCancelledException();
						while (cancels != null) {
							exc.addSuppressed(cancels.item);
							cancels = cancels.next;
						}
						return exc;
					}
				}
				return null;
			}

			private void copyFrom(State s) {
				this.offeredCount = s.offeredCount;
				this.doneCount = s.doneCount;
				this.finishIfEmpty = s.finishIfEmpty;
				this.exceptionState = s.exceptionState;
			}

		}

		private static class ThreadState<T> {
			@SuppressWarnings("rawtypes")
			private static final AtomicReferenceFieldUpdater<ThreadUtils.DynamicSmartSupplier.ThreadState, Object> ARFU_task = AtomicReferenceFieldUpdater
					.newUpdater(ThreadUtils.DynamicSmartSupplier.ThreadState.class, Object.class, "task");

			/**
			 * {@link #task} marker object, to signal that the task was already taken to process by a thread.
			 * <p>
			 * If {@link #task} has this identity value, then no more tasks can be offered to it
			 */
			static final Object TASK_MARKER_TAKEN = new Object();
			/**
			 * {@link #task} marker object, to signal that no tasks have been offered to the state yet.
			 */
			static final Object TASK_MARKER_UNOFFERED = new Object();

			private final Thread thread;
			@SuppressWarnings("unchecked")
			volatile T task = (T) TASK_MARKER_UNOFFERED;

			public ThreadState(Thread thread) {
				this.thread = thread;
			}

			public boolean offer(T task) {
				if (!ARFU_task.compareAndSet(this, TASK_MARKER_UNOFFERED, task)) {
					return false;
				}
				LockSupport.unpark(thread);
				return true;
			}

			public void wakeUp() {
				LockSupport.unpark(thread);
			}
		}

		protected final ConcurrentPrependAccumulator<ThreadState<T>> waitingThreads = new ConcurrentPrependAccumulator<>();

		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ThreadUtils.DynamicSmartSupplier, State> ARFU_state = AtomicReferenceFieldUpdater
				.newUpdater(ThreadUtils.DynamicSmartSupplier.class, State.class, "state");

		private volatile State state = new State();

		@Override
		@SuppressWarnings("unchecked")
		public T get(Thread currentthread) {
			State s = this.state;
			if (isAborted() || (s.finishIfEmpty && s.doneCount == s.offeredCount)) {
				return null;
			}

			ThreadState<T> tstate = new ThreadState<>(currentthread);
			waitingThreads.add(tstate);

			long currentnanos = System.nanoTime();
			//XXX decrease the waiting time based on an optimal thread count
			long breaknanos = currentnanos + 60 * DateUtils.NANOS_PER_SECOND;
			while (true) {
				long towait = (breaknanos - currentnanos) / DateUtils.NANOS_PER_MS;
				LockSupport.parkNanos(towait);
				//park returned because of interruption, spuriously, or new task 
				if (currentthread.isInterrupted()) {
					cancelled("Interrupted.");
					break;
				}
				if (isAborted()) {
					break;
				}
				T task = tstate.task;
				if (task != ThreadState.TASK_MARKER_UNOFFERED) {
					tstate.task = (T) ThreadState.TASK_MARKER_TAKEN;
					return task;
				}
				s = this.state;
				if (s.finishIfEmpty && s.doneCount == s.offeredCount) {
					break;
				}

				currentnanos = System.nanoTime();
				if (currentnanos - breaknanos >= 0) {
					break;
				}
			}
			//get the task if it was set between checking just in case.
			T task = (T) ThreadState.ARFU_task.getAndSet(tstate, ThreadState.TASK_MARKER_TAKEN);
			if (task != ThreadState.TASK_MARKER_UNOFFERED) {
				return task;
			}
			return null;
		}

		@Override
		public boolean isAllTasksProcessed() {
			State s = this.state;
			return s.doneCount == s.offeredCount;
		}

		public void finish() {
			State s = ARFU_state.updateAndGet(this, new State()::finish);
			checkFinishTaskCounts(s);
		}

		private void checkFinishTaskCounts(State s) {
			if (s.doneCount == s.offeredCount) {
				unparkWaitingAndClear();
			}
		}

		private void unparkWaitingAndClear() {
			Iterator<ThreadState<T>> it = waitingThreads.clearAndIterator();
			while (it.hasNext()) {
				it.next().wakeUp();
			}
		}

		public boolean offer(T element) {
			ARFU_state.updateAndGet(this, new State()::offer);
			while (true) {
				ThreadState<T> threadtarget = waitingThreads.take();
				if (threadtarget == null) {
					return false;
				}
				if (threadtarget.offer(element)) {
					return true;
				}
			}
		}

		@Override
		public void done() {
			State s = ARFU_state.updateAndGet(this, new State()::done);
			if (s.finishIfEmpty) {
				checkFinishTaskCounts(s);
			}
		}

		public void reset() {
			ARFU_state.set(this, new State());
			unparkWaitingAndClear();
		}

		@Override
		public void aborted(ParallelExecutionAbortedException e) {
			State ns = new State();
			ARFU_state.updateAndGet(this, s -> ns.aborted(s, e));
			unparkWaitingAndClear();
		}

		@Override
		public void cancelled() {
			cancelledWithException(new ParallelExecutionCancelledException());
		}

		private void cancelledWithException(ParallelExecutionCancelledException e) {
			State ns = new State();
			ARFU_state.updateAndGet(this, s -> ns.cancelled(s, e));
			unparkWaitingAndClear();
		}

		@Override
		public void cancelled(String message) {
			cancelledWithException(new ParallelExecutionCancelledException(message));
		}

		@Override
		public void cancelled(Throwable cause) {
			cancelledWithException(new ParallelExecutionCancelledException(cause));
		}

		@Override
		public void failed(Throwable e) {
			State ns = new State();
			ARFU_state.updateAndGet(this, s -> ns.failed(s, e));
			unparkWaitingAndClear();
		}

		@Override
		public boolean isAborted() {
			//abortion is not relevant for work pool
			return false;
		}

		@Override
		public ParallelExecutionException constructException() {
			return this.state.constructException();
		}
	}

	private static class WorkerThreadRunnable<T, C> implements Runnable {
		private ExceptionHoldingSupplier<? extends T> it;
		private ThrowingContextConsumer<? super T, ? super C> worker;

		private OperationCancelMonitor monitor;

		private T workObject;
		private Supplier<? extends C> contextSupplier;

		public WorkerThreadRunnable(ExceptionHoldingSupplier<T> it, T initworkobject,
				ThrowingContextConsumer<? super T, ? super C> worker, Supplier<? extends C> contextSupplier,
				OperationCancelMonitor monitor) {
			this.it = it;
			this.worker = worker;
			this.monitor = monitor;
			this.workObject = initworkobject;
			this.contextSupplier = contextSupplier;
		}

		@Override
		public void run() {
			Thread currentthread = Thread.currentThread();
			C context = this.contextSupplier == null ? null : this.contextSupplier.get();
			T workobj = this.workObject;
			OperationCancelMonitor monitor = this.monitor;
			ExceptionHoldingSupplier<? extends T> it = this.it;
			ThrowingContextConsumer<? super T, ? super C> worker = this.worker;

			//null them out to allow garbage collection
			this.workObject = null;
			this.contextSupplier = null;
			this.monitor = null;
			this.worker = null;
			this.it = null;

			//null out the workobj after calling the worker to allow garbage collection between waiting

			callWorker(worker, workobj, context, it, monitor);
			workobj = null;

			boolean interruptchecked = false;
			while (!it.isAborted()) {
				if (!interruptchecked && currentthread.isInterrupted()) {
					interruptchecked = true;
					it.cancelled("Interrupted.");
					//continue instead of break as the supplier may choose not to abort the thread
					continue;
				}
				workobj = it.get(currentthread);
				if (workobj == null) {
					//no more items
					break;
				}
				callWorker(worker, workobj, context, it, monitor);
				workobj = null;
			}
		}
	}

	private static final class IteratorDelegateSupplier<T> implements Supplier<T> {
		private volatile Iterator<T> it;
		private ReentrantLock lock = new ReentrantLock();

		private IteratorDelegateSupplier(Iterator<T> it) {
			this.it = it;
		}

		@SuppressWarnings("unchecked")
		@Override
		public T get() {
			Iterator<T> iter = it;
			if (iter == null) {
				return null;
			}
			lock.lock();
			try {
				if (!iter.hasNext()) {
					this.it = null;
					return null;
				}
				T n = iter.next();
				if (n == null) {
					return (T) SUPPLIER_NULL_ELEMENT_PLACEHOLDER;
				}
				return n;
			} finally {
				lock.unlock();
			}
		}
	}

	private static final class ExclusiveLock extends AbstractQueuedSynchronizer implements Lock {
		private static final long serialVersionUID = 1L;

		private static final int STATE_AVAILABLE = 0;
		private static final int STATE_LOCKED = 1;

		@Override
		public void unlock() {
			release(0);
		}

		@Override
		protected boolean tryRelease(int ignored) {
			if (getExclusiveOwnerThread() != Thread.currentThread()) {
				throw new IllegalMonitorStateException("Exclusive lock is not owned by current thread.");
			}
			if (!compareAndSetState(STATE_LOCKED, STATE_AVAILABLE)) {
				return false;
			}
			setExclusiveOwnerThread(null);
			return true;
		}

		@Override
		protected boolean tryAcquire(int ignored) {
			if (compareAndSetState(STATE_AVAILABLE, STATE_LOCKED)) {
				setExclusiveOwnerThread(Thread.currentThread());
				return true;
			}
			checkReentrancy();
			return false;
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			if (!tryAcquireNanos(0, unit.toNanos(time))) {
				return false;
			}
			return true;
		}

		@Override
		public boolean tryLock() {
			if (!tryAcquire(0)) {
				return false;
			}
			return true;
		}

		@Override
		public Condition newCondition() {
			return new ConditionObject();
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			acquireInterruptibly(0);
		}

		@Override
		public void lock() {
			acquire(0);
		}

		@Override
		protected boolean isHeldExclusively() {
			return getExclusiveOwnerThread() == Thread.currentThread();
		}

		private void checkReentrancy() {
			if (getExclusiveOwnerThread() == Thread.currentThread()) {
				throw new IllegalThreadStateException("Reentrant attempt for acquiring exclusive lock.");
			}
		}

		//serialization is really not recommended by us for concurrency objects, but handle it here nonetheless
		private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
			s.defaultReadObject();
			setState(STATE_AVAILABLE); // reset to unlocked state
		}
	}

	private ThreadUtils() {
		throw new UnsupportedOperationException();
	}

}
