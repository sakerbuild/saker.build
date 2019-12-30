package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * {@link InputStream} and {@link ByteSource} implementation that forwards its calls to an underlying
 * {@link InputStream}.
 */
public class InputStreamByteSource extends InputStream implements ByteSource {
	/**
	 * The underlying {@link InputStream}.
	 */
	protected final InputStream is;

	/**
	 * Creates a new instance with the given underlying input stream.
	 * 
	 * @param is
	 *            The input stream.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public InputStreamByteSource(InputStream is) throws NullPointerException {
		Objects.requireNonNull(is, "input stream");
		this.is = is;
	}

	@Override
	public int read() throws IOException {
		return is.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return is.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return is.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		return is.skip(n);
	}

	@Override
	public int available() throws IOException {
		return is.available();
	}

	@Override
	public void close() throws IOException {
		is.close();
	}

	@Override
	public void mark(int readlimit) {
		is.mark(readlimit);
	}

	@Override
	public void reset() throws IOException {
		is.reset();
	}

	@Override
	public boolean markSupported() {
		return is.markSupported();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + is + "]";
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		return StreamUtils.readFromStream(is, buffer);
	}

}
