package saker.build.thirdparty.saker.util.thread;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

class BooleanLatchImpl extends AbstractQueuedSynchronizer implements BooleanLatch {
	private static final long serialVersionUID = 1L;

	private static final int STATE_INIT = 0;
	private static final int STATE_SIGNALLED = 1;

	@Override
	public boolean isSignalled() {
		return getState() == STATE_SIGNALLED;
	}

	@Override
	public void signal() {
		releaseShared(0); // arg ignored
	}

	@Override
	public void await() throws InterruptedException {
		if (isSignalled()) {
			//if signalled, return immediately
			//this is to not throw interrupted exception if we enter this method when already interrupted
			return;
		}
		acquireSharedInterruptibly(0); // arg ignored
	}

	@Override
	public void awaitUninterruptibly() {
		acquireShared(0); // arg ignored
	}

	@Override
	public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
		if (isSignalled()) {
			//if signalled, return immediately
			//this is to not throw interrupted exception if we enter this method when already interrupted
			return true;
		}
		return tryAcquireSharedNanos(0, unit.toNanos(timeout)); // arg ignored
	}

	@Override
	public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
		// we implement the uninterruptible wait, since that is not supported in AbstractQueuedSynchronizer
		if (isSignalled()) {
			return true;
		}
		final long nanotimeout = unit.toNanos(timeout);
		boolean interrupted = false;
		try {
			//need to get the deadline before the first tryAcquireSharedNanos call
			long deadline = System.nanoTime() + nanotimeout;

			try {
				return tryAcquireSharedNanos(0, nanotimeout); // arg ignored
			} catch (InterruptedException e) {
				interrupted = true;
			}

			while (true) {
				final long remain = deadline - System.nanoTime();
				if (remain <= 0L) {
					//timeout, not acquired
					return false;
				}
				try {
					return tryAcquireSharedNanos(0, remain); // arg ignored
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	protected boolean tryReleaseShared(int arg) {
		setState(STATE_SIGNALLED);
		return true;
	}

	@Override
	protected int tryAcquireShared(int arg) {
		return isSignalled() ? 1 : -1;
	}

}
