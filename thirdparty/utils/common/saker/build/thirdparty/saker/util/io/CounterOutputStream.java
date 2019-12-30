package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Filtering output stream that counts the number of bytes written to the subject output stream.
 * <p>
 * This stream is not thread safe.
 * 
 * @see #getCount()
 */
public class CounterOutputStream extends OutputStream {
	private final OutputStream out;
	private long count;

	/**
	 * Creates a new instance for the given stream.
	 * 
	 * @param out
	 *            The subject stream.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public CounterOutputStream(OutputStream out) throws NullPointerException {
		Objects.requireNonNull(out, "output stream");
		this.out = out;
	}

	/**
	 * Gets the number of bytes written to this stream.
	 * 
	 * @return The count.
	 */
	public long getCount() {
		return count;
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		++count;
	}

	@Override
	public void write(byte[] b) throws IOException {
		out.write(b);
		count += b.length;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
		count += len;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

}
