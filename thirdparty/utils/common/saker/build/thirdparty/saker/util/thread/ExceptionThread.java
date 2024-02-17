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

import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;

/**
 * {@link Thread} subclass that is capable of running {@link ThrowingRunnable ThrowingRunnables} and store any thrown
 * exceptions to retrieve later.
 * <p>
 * If the thread throws an exception during its lifetime, it will be caught by this class and stored to be retrieved
 * after it has been joined.
 * <p>
 * Subclasses can override {@link #runImpl()} instead of {@link #run()} to execute their work.
 * <p>
 * The class will catch all exceptions that occur in its run method, and will rethrow any exceptions that are not safe
 * to recover from. Safely recoverable exceptions are: {@link StackOverflowError}, {@link OutOfMemoryError},
 * {@link LinkageError}, {@link ServiceConfigurationError}, {@link AssertionError}, {@link Exception}. Other
 * {@link Throwable Throwables} and {@link Error Errors} will be thrown from the {@link #run()} method of the thread.
 * All exceptions are going to be stored for later retrieval nonetheless.
 * 
 * @see #getException()
 */
public class ExceptionThread extends Thread {
	private static final AtomicReferenceFieldUpdater<ExceptionThread, Throwable> ARFU_exc = AtomicReferenceFieldUpdater
			.newUpdater(ExceptionThread.class, Throwable.class, "exc");

	private volatile Throwable exc;

	/**
	 * @see Thread#Thread()
	 */
	public ExceptionThread() {
		super();
	}

	/**
	 * @see Thread#Thread(Runnable, String)
	 */
	public ExceptionThread(ThrowingRunnable target, String name) {
		super(Functionals.sneakyThrowingRunnable(target), name);
	}

	/**
	 * @see Thread#Thread(Runnable)
	 */
	public ExceptionThread(ThrowingRunnable target) {
		super(Functionals.sneakyThrowingRunnable(target));
	}

	/**
	 * @see Thread#Thread(String)
	 */
	public ExceptionThread(String name) {
		super(name);
	}

	/**
	 * @see Thread#Thread(ThreadGroup, Runnable, String, long)
	 */
	public ExceptionThread(ThreadGroup group, ThrowingRunnable target, String name, long stackSize) {
		super(group, Functionals.sneakyThrowingRunnable(target), name, stackSize);
	}

	/**
	 * @see Thread#Thread(ThreadGroup, Runnable, String)
	 */
	public ExceptionThread(ThreadGroup group, ThrowingRunnable target, String name) {
		super(group, Functionals.sneakyThrowingRunnable(target), name);
	}

	/**
	 * @see Thread#Thread(ThreadGroup, Runnable)
	 */
	public ExceptionThread(ThreadGroup group, ThrowingRunnable target) {
		super(group, Functionals.sneakyThrowingRunnable(target));
	}

	/**
	 * @see Thread#Thread(ThreadGroup, String)
	 */
	public ExceptionThread(ThreadGroup group, String name) {
		super(group, name);
	}

	/**
	 * @see Thread#Thread(Runnable, String)
	 */
	public ExceptionThread(Runnable target, String name) {
		super(target, name);
	}

	/**
	 * @see Thread#Thread(Runnable)
	 */
	public ExceptionThread(Runnable target) {
		super(target);
	}

	/**
	 * @see Thread#Thread(ThreadGroup, Runnable, String, long)
	 */
	public ExceptionThread(ThreadGroup group, Runnable target, String name, long stackSize) {
		super(group, target, name, stackSize);
	}

	/**
	 * @see Thread#Thread(ThreadGroup, Runnable, String)
	 */
	public ExceptionThread(ThreadGroup group, Runnable target, String name) {
		super(group, target, name);
	}

	/**
	 * @see Thread#Thread(ThreadGroup, Runnable)
	 */
	public ExceptionThread(ThreadGroup group, Runnable target) {
		super(group, target);
	}

	@Override
	public final void run() {
		try {
			runImpl();
		} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
				| Exception e) {
			this.exc = e;
		} catch (Throwable e) {
			this.exc = e;
			//rethrow any more fatal errors
			throw e;
		}
	}

	/**
	 * Runs the thread.
	 * <p>
	 * Subclasses can override this method instead of {@link #run()}, and any exception thrown by this method will be
	 * stored to retrieve when the thread is joined.
	 * <p>
	 * The default implementation calls {@link #run() super.run()}.
	 * 
	 * @throws Exception
	 *             If the thread fails to successfully complete.
	 */
	protected void runImpl() throws Exception {
		super.run();
	}

	/**
	 * Joins the thread the same way as {@link #join()}, and throws any exception that were caught in the thread.
	 * 
	 * @throws InterruptedException
	 *             If the current thread was interrupted during waiting, or an {@link InterruptedException} was caught
	 *             in the thread.
	 * @throws Throwable
	 *             If an exception was caught in the thread. This is usually an {@link Exception} or {@link Error}
	 *             subclass.
	 * @see #join()
	 */
	public void joinThrow() throws InterruptedException, Throwable {
		join();
		if (exc != null) {
			throw exc;
		}
	}

	/**
	 * Joins the thread the same way as {@link #join()}, and returns any exception that were caught in the thread.
	 * 
	 * @return The exception that was thrown by the thread, or <code>null</code> if none.
	 * @throws InterruptedException
	 *             If the current thread was interrupted during waiting.
	 * @see #join()
	 */
	public Throwable joinGetException() throws InterruptedException {
		join();
		return exc;
	}

	/**
	 * Joins the thread the same way as {@link #join()}, and {@linkplain #takeException() takes} any exception that were
	 * caught in the thread.
	 * 
	 * @return The exception that was thrown by the thread, or <code>null</code> if none.
	 * @throws InterruptedException
	 *             If the current thread was interrupted during waiting.
	 * @see #join()
	 * @see #takeException()
	 */
	public Throwable joinTakeException() throws InterruptedException {
		join();
		return takeException();
	}

	/**
	 * Gets the exception thrown by the thread.
	 * <p>
	 * Callers should make sure that the thread is finished before calling this.
	 * 
	 * @return The exception, or <code>null</code> if none was thrown, or the thread hasn't finished yet.
	 */
	public Throwable getException() {
		return exc;
	}

	/**
	 * Takes the exception from the thread object.
	 * <p>
	 * Callers should make sure that the thread is finished before calling this.
	 * <p>
	 * This method will check if the thread has thrown an exception, and will atomically exchange it to a
	 * <code>null</code> internally. Further calls to {@link #getException()} or {@link #takeException()} will return
	 * <code>null</code>.
	 * 
	 * @return The exception, or <code>null</code> if none was thrown, or the thread hasn't finished yet.
	 */
	public Throwable takeException() {
		return ARFU_exc.getAndSet(this, null);
	}
}
