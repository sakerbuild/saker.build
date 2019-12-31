package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Filtering input stream that counts the number of bytes read from the subject input stream.
 * <p>
 * This stream is not thread safe.
 * 
 * @see #getCount()
 */
public class CounterInputStream extends InputStream {
	private final InputStream in;
	private long count;

	/**
	 * Creates a new instance for the given stream.
	 * 
	 * @param in
	 *            The subject stream.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public CounterInputStream(InputStream in) throws NullPointerException {
		Objects.requireNonNull(in, "input stream");
		this.in = in;
	}

	/**
	 * Gets the number of bytes read from this stream.
	 * <p>
	 * Skipped bytes are added to this number.
	 * 
	 * @return The count.
	 */
	public long getCount() {
		return count;
	}

	@Override
	public int read() throws IOException {
		int res = in.read();
		if (res >= 0) {
			++count;
		}
		return res;
	}

	@Override
	public int read(byte[] b) throws IOException {
		int res = in.read(b);
		if (res >= 0) {
			count += res;
		}
		return res;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int res = in.read(b, off, len);
		if (res >= 0) {
			count += res;
		}
		return res;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	//we cannot forward skip, as if the inputstream uses the read methods to skip, then the skipped bytes are counted twice

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
