package saker.build.ide.support.util;

import java.util.function.Consumer;

import saker.build.thirdparty.saker.util.function.ThrowingSupplier;

public class ThreadedResourceLoader<T> {
	private final Object lock = new Object();
	private boolean closed = false;
	private Thread loaderThread;

	public ThreadedResourceLoader() {
	}

	public void startLoading(ThrowingSupplier<? extends T> loader, Consumer<? super T> success,
			Consumer<? super Throwable> fail) throws InterruptedException {
		synchronized (lock) {
			if (closed) {
				throw new IllegalStateException("closed");
			}
			interruptWaitLoaderThreadLocked();
			loaderThread = new Thread(() -> {
				T result;
				try {
					result = loader.get();
				} catch (Exception e) {
					if (fail != null) {
						fail.accept(e);
					}
					return;
				}
				success.accept(result);
			}, "Resource loader");
			//set daemon status as true, as we're loading a resource, not doing other operations
			//may be externally configureable
			loaderThread.setDaemon(true);
			loaderThread.start();
		}
	}

	public void finishLoading() throws InterruptedException {
		synchronized (lock) {
			if (loaderThread != null) {
				loaderThread.join();
				loaderThread = null;
			}
		}
	}

	private void interruptWaitLoaderThreadLocked() throws InterruptedException {
		if (loaderThread != null) {
			//currently a thread is loading
			if (loaderThread.isAlive()) {
				//check for aliveness, to avoid unnecessary interruption and joining
				loaderThread.interrupt();
				loaderThread.join();
			}
			loaderThread = null;
		}
	}

	public void close() throws InterruptedException {
		synchronized (lock) {
			if (closed) {
				return;
			}
			closed = true;
			try {
				interruptWaitLoaderThreadLocked();
			} catch (InterruptedException e) {
				//interrupt and throw
				//we need to interrupt, as the close method doesn't usually throw InterruptedException, so the interruption
				//    may needs to be discovered by the caller later
				//we throw to signal the exception
				Thread.currentThread().interrupt();
				throw e;
			}
		}
	}
}
