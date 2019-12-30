package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * Stream class providing reading and writing operations to an internal buffer.
 * <p>
 * The class serves as a mediator for a stream writer and stream reader. Any data that is written to the stream is
 * buffered in an internal buffer. Reading from the buffer will remove the previously written bytes from the buffer.
 * <p>
 * The above behaviour means that a stream instance can be concurrently used by a producer and consumer thread. Any read
 * operations will read the currently available data if any, or block until some data is written to it. When data is
 * written, the consumer threads are notified about this and the bytes may be read.
 * <p>
 * Closing the stream will not discard previously written data from it, they may still be readable.
 * <p>
 * This class extends {@link OutputStream}, and implements {@link ByteSource} and {@link ByteSink}. To retrieve an
 * {@link InputStream} instance, use {@link ByteSource#toInputStream(ByteSource)}.
 */
public class ReadWriteBufferOutputStream extends OutputStream implements ByteSource, ByteSink {
	private UnsyncByteArrayOutputStream baos;
	private int position = 0;
	private volatile boolean closed = false;

	/**
	 * Creates a new instance.
	 */
	public ReadWriteBufferOutputStream() {
		baos = new UnsyncByteArrayOutputStream();
	}

	/**
	 * Creates a new instance with the argument buffer size.
	 * 
	 * @param size
	 *            The buffer size.
	 * @throws NegativeArraySizeException
	 *             If the size is negative.
	 */
	public ReadWriteBufferOutputStream(int size) throws NegativeArraySizeException {
		baos = new UnsyncByteArrayOutputStream(size);
	}

	//XXX we should add readIfAvailable or something methods, that doesn't block

	@Override
	public synchronized int read() throws IOException {
		while (true) {
			int available = baos.count - position;
			if (available > 0) {
				int result = Byte.toUnsignedInt(baos.buf[position++]);
				compact();
				return result;
			}
			if (closed) {
				return -1;
			}
			try {
				this.wait();
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}
	}

	/**
	 * Reads bytes into the argument byte array, waiting for some if not available.
	 * <p>
	 * If there are any available bytes in the stream, they will be copied into the buffer, and returned immediately.
	 * <p>
	 * Else the method will wait for bytes to be written to the stream, and will return them accordingly.
	 * 
	 * @param buffer
	 *            The buffer to read the bytes into.
	 * @return The number of bytes read, or -1 if the stream has been closed.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 */
	public synchronized int read(byte[] buffer) throws IOException, NullPointerException {
		return read(buffer, 0, buffer.length);
	}

	/**
	 * Reads bytes into the argument range of the specified byte array, waiting for some if not available.
	 * <p>
	 * If there are any available bytes in the stream, they will be copied into the buffer, and returned immediately.
	 * <p>
	 * Else the method will wait for bytes to be written to the stream, and will return them accordingly.
	 * 
	 * @param buffer
	 *            The buffer to read the bytes into.
	 * @param offset
	 *            The offset index where to start copying the read bytes into the buffer.
	 * @param length
	 *            The number of bytes to read.
	 * @return The number of bytes read, or -1 if the stream has been closed.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is outside of the array.
	 */
	public synchronized int read(byte[] buffer, int offset, int length)
			throws IOException, IndexOutOfBoundsException, NullPointerException {
		ArrayUtils.requireArrayRange(buffer, offset, length);
		while (true) {
			int available = baos.count - position;
			if (length <= available) {
				System.arraycopy(baos.buf, position, buffer, offset, length);
				position += length;
				compact();
				return length;
			}
			if (available > 0) {
				System.arraycopy(baos.buf, position, buffer, offset, available);
				position += available;
				compact();
				return available;
			}
			if (closed) {
				return -1;
			}
			//none available
			try {
				this.wait();
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}
	}

	@Override
	public synchronized ByteArrayRegion read(int count) throws IOException {
		if (count <= 0) {
			return ByteArrayRegion.EMPTY;
		}
		while (true) {
			int available = baos.count - position;
			if (available > 0) {
				if (available >= count) {
					//more available than count
					byte[] copied = Arrays.copyOfRange(baos.buf, position, count);
					position += count;
					return ByteArrayRegion.wrap(copied);
				}
				//less available than count.
				//reset the counts
				byte[] copied = Arrays.copyOfRange(baos.buf, position, available);
				this.position = 0;
				baos.count = 0;
				return ByteArrayRegion.wrap(copied);
			}
			if (closed) {
				return ByteArrayRegion.EMPTY;
			}
			//none available, wait
			try {
				this.wait();
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}
	}

	@Override
	public synchronized void write(int b) throws IOException {
		checkClosed();
		baos.write(b);
		this.notifyAll();
	}

	@Override
	public synchronized void write(byte[] b, int off, int len) throws IOException {
		checkClosed();
		baos.write(b, off, len);
		this.notifyAll();
	}

	@Override
	public synchronized void write(byte[] b) throws IOException {
		checkClosed();
		baos.write(b);
		this.notifyAll();
	}

	@Override
	public synchronized void write(ByteArrayRegion buf) throws IOException, NullPointerException {
		checkClosed();
		baos.write(buf);
		this.notifyAll();
	}

	/**
	 * Gets the number of bytes that can be read without blocking.
	 * 
	 * @return The number of available bytes.
	 */
	public synchronized int available() {
		return baos.count - position;
	}

	/**
	 * Same as {@link #readFrom(ByteSource)}, but with an {@link InputStream} argument.
	 * 
	 * @param in
	 *            The input stream.
	 * @return The number of bytes read and written to this stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public synchronized long readFrom(InputStream in) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		checkClosed();
		long result = baos.readFrom(in);
		this.notifyAll();
		return result;
	}

	@Override
	public synchronized long readFrom(ByteSource in) throws IOException, NullPointerException {
		checkClosed();
		return baos.readFrom(in);
	}

	/**
	 * Same as {@link #writeTo(ByteSink)}, but with an {@link OutputStream} argument.
	 * 
	 * @param out
	 *            The output stream.
	 * @return The number of bytes written to the output stream. Always zero or greater.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public synchronized long writeTo(OutputStream out) throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		long result = 0;
		while (true) {
			int available = baos.count - position;
			if (available > 0) {
				baos.writeTo(out, position, available);
				this.baos.count = 0;
				this.position = 0;
				result += available;
				continue;
			}
			if (closed) {
				break;
			}
			//none available, wait
			try {
				this.wait();
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * <b>Warning:</b> This method doesn't return until the stream is closed or the current thread is interrupted, which
	 * is signaled by a thrown {@link InterruptedIOException}.
	 */
	@Override
	public synchronized long writeTo(ByteSink out) throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		long result = 0;
		while (true) {
			int available = baos.count - position;
			if (available > 0) {
				baos.writeTo(out, position, available);
				this.baos.count = 0;
				this.position = 0;
				result += available;
				continue;
			}
			if (closed) {
				break;
			}
			//none available, wait
			try {
				this.wait();
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}
		return result;
	}

	@Override
	public void close() {
		synchronized (this) {
			closed = true;
			this.notifyAll();
		}
		baos.close();
	}

	@Override
	public synchronized int read(ByteRegion buffer) throws IOException {
		if (buffer instanceof ByteArrayRegion) {
			//we can read directly to the byte array
			ByteArrayRegion bar = (ByteArrayRegion) buffer;
			return read(bar.getArray(), bar.getOffset(), bar.getLength());
		}
		byte[] bytebuf = new byte[Math.min(buffer.getLength(), 1024 * 4)];
		int r = read(bytebuf);
		if (r <= 0) {
			return r;
		}
		buffer.put(0, ByteArrayRegion.wrap(baos.buf, 0, r));
		return r;
	}

	private void compact() {
		if (position == baos.count) {
			baos.count = 0;
			position = 0;
		} else if (baos.count >= position * 2) {
			//position is past half count
			int available = baos.count - position;
			System.arraycopy(baos.buf, position, baos.buf, 0, available);
			baos.count -= position;
			position = 0;
		}
	}

	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("closed");
		}
	}
}
