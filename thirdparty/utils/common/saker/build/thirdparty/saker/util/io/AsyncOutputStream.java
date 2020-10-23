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
package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link OutputStream} implementation that takes write requests and executes the writing to a subject stream
 * asynchronously.
 * <p>
 * An instance of this class is created for a given output stream. The class will spawn its own thread that executes the
 * write operations to the given stream.
 * <p>
 * When a write request is issued to this stream, it will take the bytes from the argument, and copy them to an internal
 * buffer. The internal buffer is asynchronously flushed to the subject output stream on the flusher thread.
 * <p>
 * Any exceptions which are thrown by the subject stream may be reported asynchronously in any future write, flush, or
 * close operations. Therefore callers should be noted, that an exception thrown from a method of this class may not be
 * directly related to the actually called method. Any exception occurring on the flusher thread will be rethrown as
 * {@link AsyncOutputIOException}.
 * <p>
 * When the async output stream is closed, or {@link #flush()} is called, all the pending write operations will be
 * finished before that method returns. Closing this stream will shut down the flusher thread, and close the subject
 * stream as well.
 * <p>
 * Using this class can be advantageous when the subject stream may block during write operations, and the caller could
 * proceed with its work instead of waiting for I/O.
 * <p>
 * If the callers fail to properly close this stream, the flushing thread will automatically quit when the
 * {@link AsyncOutputStream} instance is garbage collected. (The flushing stream is also a
 * {@linkplain Thread#setDaemon(boolean) daemon thread}.)
 * <p>
 * The flushing stream is started with a {@linkplain Thread#setPriority(int) low priority}, therefore longer delays can
 * occurr if the CPU is doing more important work.
 */
public class AsyncOutputStream extends OutputStream implements ByteSink {
	/**
	 * Exception class that is signaling an {@link IOException} that occurred in an other time than the current call.
	 * <p>
	 * This exception is thrown when the subject stream throws an exception in {@link AsyncOutputStream} on the flusher
	 * thread.
	 */
	public static class AsyncOutputIOException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates a new instance with the given cause.
		 * 
		 * @param cause
		 *            The cause.
		 */
		public AsyncOutputIOException(IOException cause) {
			super(cause);
		}
	}

	private static final AtomicReferenceFieldUpdater<AsyncOutputStream, Thread> ARFU_flushingThread = AtomicReferenceFieldUpdater
			.newUpdater(AsyncOutputStream.class, Thread.class, "flushingThread");

	private volatile IOException exception = null;
	private volatile Thread flushingThread;
	private final OutputStream out;
	private volatile BlockingQueue<AsyncAction> commands = new LinkedBlockingQueue<>();
	private UnsyncByteArrayOutputStream buffer = new UnsyncByteArrayOutputStream();
	private UnsyncByteArrayOutputStream swapBuffer = new UnsyncByteArrayOutputStream();

	protected final ReentrantLock lock = new ReentrantLock();

	private final AsyncAction bufferWriterAction = new AsyncAction() {
		@Override
		public void accept(OutputStream os) throws IOException {
			flushBufferOnThread(os);
		}
	};

	/**
	 * Creates a new instance for the given stream.
	 * 
	 * @param out
	 *            The stream.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public AsyncOutputStream(OutputStream out) throws NullPointerException {
		this(out, null);
	}

	/**
	 * Creates a new instance that uses the given thread factory.
	 * <p>
	 * The specified thread factory will be used to create the asynchronously flushing thread. The thread factory should
	 * set if the thread is {@linkplain Thread#setDaemon(boolean) daemon} and set its
	 * {@linkplain Thread#setPriority(int) priority}.
	 * 
	 * @param out
	 *            The output stream.
	 * @param threadfactory
	 *            The thread factory. May be <code>null</code> in which case a new {@linkplain Thread#setDaemon(boolean)
	 *            daemon thread} with {@linkplain Thread#MIN_PRIORITY minimum} {@linkplain Thread#setPriority(int)
	 *            priority} is created.
	 * @throws NullPointerException
	 *             If the output stream is <code>null</code>.
	 * @since saker.util 0.8.2
	 */
	public AsyncOutputStream(OutputStream out, ThreadFactory threadfactory) throws NullPointerException {
		Objects.requireNonNull(out, "out");
		this.out = out;

		BlockingQueue<AsyncAction> cmds = commands;
		WeakReference<AsyncOutputStream> weakref = new WeakReference<>(this);

		Thread thread;
		if (threadfactory == null) {
			thread = new Thread(() -> runFlushing(weakref, cmds), "Async stream-" + out);
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
		} else {
			thread = threadfactory.newThread(() -> runFlushing(weakref, cmds));
			if (thread == null) {
				throw new NullPointerException("ThreadFactory returned null thread.");
			}
		}
		thread.setContextClassLoader(null);
		thread.start();

		flushingThread = thread;
	}

	private static void runFlushing(WeakReference<? extends AsyncOutputStream> osref,
			BlockingQueue<? extends AsyncAction> commands) {
		try {
			while (true) {
				AsyncAction run = commands.poll(5, TimeUnit.SECONDS);
				AsyncOutputStream os = osref.get();
				if (os == null) {
					break;
				}
				if (run == null) {
					if (os.flushingThread == null) {
						os.lock.lockInterruptibly();
						try {
							os.commands = null;
						} finally {
							os.lock.unlock();
						}
						break;
					}
					continue;
				}
				try {
					run.accept(os.out);
					continue;
				} catch (IOException e) {
					os.exception = e;
				} catch (RuntimeException e) {
					os.exception = new IOException(e);
				}
				os.flushingThread = null;
				os.lock.lockInterruptibly();
				try {
					os.commands = null;
				} finally {
					os.lock.unlock();
				}
				break;
			}
		} catch (InterruptedException e) {
		} finally {
			//signal closing
			AsyncOutputStream os = osref.get();
			if (os != null) {
				os.lock.lock();
				try {
					os.commands = null;
				} finally {
					os.lock.unlock();
				}
			}
			for (AsyncAction p; (p = commands.poll()) != null;) {
				if (p instanceof DeliverAction) {
					//it it was a delivering runnable, notify it
					((DeliverAction) p).notifyDelivery();
				}
			}
		}
	}

	private void flushBufferOnThread(OutputStream os) throws IOException {
		UnsyncByteArrayOutputStream toflush;
		lock.lock();
		try {
			toflush = buffer;
			if (toflush.isEmpty()) {
				return;
			}
			buffer = swapBuffer;
			swapBuffer = toflush;
		} finally {
			lock.unlock();
		}
		toflush.writeTo(os);
		toflush.reset();
	}

	private void throwOnException() throws AsyncOutputIOException {
		IOException exc = this.exception;
		if (exc != null) {
			throw new AsyncOutputIOException(exc);
		}
	}

	@Override
	public void write(int b) throws IOException {
		lock.lock();
		try {
			throwOnException();
			BlockingQueue<AsyncAction> cmds = commands;
			if (cmds == null) {
				throw new IOException("closed.");
			}
			buffer.write(b);
			cmds.add(bufferWriterAction);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		lock.lock();
		try {
			throwOnException();
			BlockingQueue<AsyncAction> cmds = commands;
			if (cmds == null) {
				throw new IOException("closed.");
			}
			buffer.write(b);
			cmds.add(bufferWriterAction);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		lock.lock();
		try {
			throwOnException();
			BlockingQueue<AsyncAction> cmds = commands;
			if (cmds == null) {
				throw new IOException("closed.");
			}
			buffer.write(b, off, len);
			cmds.add(bufferWriterAction);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException, NullPointerException {
		buf.writeTo((OutputStream) this);
	}

	private static abstract class AsyncAction {
		public abstract void accept(OutputStream os) throws IOException;
	}

	private static class FlusherAction extends DeliverAction {
		@Override
		public void accept(OutputStream os) throws IOException {
			try {
				os.flush();
			} finally {
				super.accept(os);
			}
		}
	}

	private static class DeliverAction extends AsyncAction {
		private volatile Thread deliveryThread = Thread.currentThread();

		@Override
		public void accept(OutputStream os) throws IOException {
			notifyDelivery();
		}

		protected void notifyDelivery() {
			Thread t = deliveryThread;
			if (t == null) {
				return;
			}
			deliveryThread = null;
			LockSupport.unpark(t);
		}

		protected void waitDelivery() throws InterruptedException {
			while (deliveryThread != null) {
				LockSupport.park();
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method also waits for the flushing to be done on the flusher thread.
	 */
	@Override
	public void flush() throws IOException {
		deliverAction(new FlusherAction());
	}

	/**
	 * Delivers any pending I/O operations to the underlying stream.
	 * <p>
	 * This method will not {@link OutputStream#flush() flush()} the underlying stream, only waits for all pending
	 * operations to finish.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InterruptedIOException
	 *             If the waiting for the delivering was interrupted, or the underlying stream threw such an exception.
	 */
	public void deliver() throws IOException, InterruptedIOException {
		deliverAction(new DeliverAction());
	}

	/**
	 * Sends a quit signal to the flushing thread, and will consider this stream closed for further operations.
	 * <p>
	 * This method will send a quit signal to the flusher thread, prompting it to finish. This method doesn't close or
	 * flush the subject stream. Any further I/O methods to this stream will report it as closed.
	 * <p>
	 * Similar to {@link #deliver()}, all pending operations will be waited.
	 */
	public void exit() {
		try {
			deliver();
		} catch (IOException e) {
		}
		commands = null;
		Thread t = ARFU_flushingThread.getAndSet(this, null);
		if (t != null) {
			t.interrupt();
		}
	}

	private void deliverAction(DeliverAction flusher) throws IOException, InterruptedIOException {
		try {
			lock.lock();
			try {
				throwOnException();
				BlockingQueue<AsyncAction> cmds = commands;
				if (cmds == null) {
					throw new IOException("closed.");
				}
				cmds.add(flusher);
			} finally {
				lock.unlock();
			}
			flusher.waitDelivery();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException();
		}
	}

	@Override
	public void close() throws IOException {
		try {
			try {
				deliver();
			} catch (IOException e) {
			}
			commands = null;
			Thread t = ARFU_flushingThread.getAndSet(this, null);
			if (t != null) {
				t.interrupt();
				try {
					t.join();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new InterruptedIOException();
				}
			}
		} finally {
			out.close();
		}
		throwOnException();
	}

}
