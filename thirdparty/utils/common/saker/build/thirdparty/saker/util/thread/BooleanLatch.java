package saker.build.thirdparty.saker.util.thread;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A single-fire boolean latch synchronization interface.
 * <p>
 * The interface describes a single-use boolean latch, which can be signalled once that in turn causes all waiters to be
 * released.
 * <p>
 * The interface is mainly based on the <code>BooleanLatch</code> example code described in the documentation of
 * {@link AbstractQueuedSynchronizer}.
 * 
 * @see BooleanLatch#newBooleanLatch()
 * @since saker.util 0.8.4
 */
public interface BooleanLatch {
	/**
	 * Signals the latch and releases all waiters.
	 */
	public void signal();

	/**
	 * Gets if the latch has been signalled.
	 * 
	 * @return <code>true</code> if the latch is signalled.
	 */
	public boolean isSignalled();

	/**
	 * Waits for the latch to be signalled.
	 * <p>
	 * If the latch is already signalled when this method is entered, it will return immediately, and will not throw
	 * {@link InterruptedException} even if the current thread is interrupted.
	 * 
	 * @throws InterruptedException
	 *             If the current thread is interrupted during waiting.
	 * @see #signal()
	 */
	public void await() throws InterruptedException;

	/**
	 * Waits for the latch to be signalled in non interruptible mode.
	 * 
	 * @see #signal()
	 */
	public void awaitUninterruptibly();

	/**
	 * Waits for the lats to be signalled or until the given timeout elapses.
	 * <p>
	 * If the latch is already signalled when this method is entered, it will return immediately, and will not throw
	 * {@link InterruptedException} even if the current thread is interrupted.
	 * 
	 * @param timeout
	 *            The timeout amount.
	 * @param unit
	 *            The unit if the amount.
	 * @return <code>true</code> if the latch has been signalled, <code>false</code> if the timeout elapsed.
	 * @throws InterruptedException
	 *             If the current thread is interrupted during waiting.
	 */
	public boolean await(long timeout, TimeUnit unit) throws InterruptedException;

	/**
	 * Waits for the lats to be signalled or until the given timeout elapses in non interruptible mode.
	 * 
	 * @param timeout
	 *            The timeout amount.
	 * @param unit
	 *            The unit if the amount.
	 * @return <code>true</code> if the latch has been signalled, <code>false</code> if the timeout elapsed.
	 */
	public boolean awaitUninterruptibly(long timeout, TimeUnit unit);

	/**
	 * Creates a new boolean latch.
	 * 
	 * @return The new latch.
	 */
	public static BooleanLatch newBooleanLatch() {
		return new BooleanLatchImpl();
	}
}
