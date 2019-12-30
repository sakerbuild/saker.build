package saker.build.thirdparty.saker.util.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Input stream class similar to {@link BufferedInputStream}, but it does not synchronize its methods and therefore is
 * not thread safe.
 * <p>
 * Closing this input stream will close its subject too.
 * <p>
 * This stream is not thread safe.
 * 
 * @see BufferedInputStream
 */
public class UnsyncBufferedInputStream extends InputStream implements ByteSource {
	/**
	 * The default buffer size.
	 */
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

	/**
	 * The subject input stream.
	 */
	protected final InputStream in;

	/**
	 * The buffer that holds internally buffered data.
	 */
	protected final byte[] buffer;
	/**
	 * The position in the buffer where buffered data for next reading operations start.
	 * <p>
	 * If {@link #pos} is greater or equals to {@link #count}, no readable data is present in the buffer.
	 */
	protected int pos;
	/**
	 * The number of valid bytes present in the {@link #buffer}.
	 */
	protected int count;

	/**
	 * Creates a new instance with the given buffer size.
	 * <p>
	 * The buffer size should be chosen to be reasonable. Choosing very small numbers can degrade the performance.
	 * 
	 * @param in
	 *            The subject input stream.
	 * @param buffersize
	 *            The buffer size.
	 * @throws NullPointerException
	 *             If the input stream is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the buffer size is less than 1.
	 */
	public UnsyncBufferedInputStream(InputStream in, int buffersize)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(in, "input stream");
		if (buffersize < 1) {
			throw new IllegalArgumentException("buffer size < 1: " + buffersize);
		}
		this.in = in;
		this.buffer = new byte[buffersize];
	}

	/**
	 * Creates a new buffered input stream for the given subject input stream.
	 * <p>
	 * The default buffer size is used: {@value #DEFAULT_BUFFER_SIZE}.
	 * 
	 * @param in
	 *            The subject input stream.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public UnsyncBufferedInputStream(InputStream in) throws NullPointerException {
		this(in, DEFAULT_BUFFER_SIZE);
	}

	@Override
	public int read() throws IOException {
		byte[] buf = buffer;
		if (pos < count) {
			return buf[pos++] & 0xFF;
		}
		//no more data in buffer, read some
		int read = in.read(buf, 0, buf.length);
		if (read <= 0) {
			return -1;
		}
		count = read;
		pos = 1;
		return buf[0] & 0xFF;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return this.read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) {
			return 0;
		}
		int avail = count - pos;
		byte[] buf = buffer;
		if (avail > 0) {
			int toread = Math.min(avail, len);
			System.arraycopy(buf, pos, b, off, toread);
			pos += toread;
			return toread;
		}
		//no data available in buffer
		if (len >= buf.length) {
			//we can read directly into the output buffer, as we would need to copy it anyway
			return in.read(b, off, len);
		}
		int read = in.read(buf, 0, buf.length);
		if (read <= 0) {
			return read;
		}
		count = read;
		int toread = Math.min(read, len);
		System.arraycopy(buf, 0, b, off, toread);
		pos = toread;
		return toread;
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		return StreamUtils.readFromStream(this, buffer);
	}

	//XXX override writeTo

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		long avail = count - pos;
		if (avail >= n) {
			pos += n;
			return n;
		}
		pos = 0;
		count = 0;
		n -= avail;
		return avail + in.skip(n);
	}

	@Override
	public int available() throws IOException {
		return count - pos;
	}

	@Override
	public void close() throws IOException {
		pos = 0;
		count = 0;
		in.close();
	}

	@Override
	public void mark(int readlimit) {
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	@Override
	public boolean markSupported() {
		return false;
	}

}
