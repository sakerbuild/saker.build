package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * {@link OutputStream} and {@link ByteSink} implementation that forwards its calls to an underlying
 * {@link OutputStream}.
 */
public class OutputStreamByteSink extends OutputStream implements ByteSink {
	static final int MAX_WRITE_COPY_SIZE = 4 * 1024 * 1024;

	/**
	 * The underlying {@link OutputStream}.
	 */
	protected final OutputStream os;

	/**
	 * Creates a new instance with the given underlying output stream.
	 * 
	 * @param os
	 *            The output stream.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public OutputStreamByteSink(OutputStream os) throws NullPointerException {
		Objects.requireNonNull(os, "output stream");
		this.os = os;
	}

	@Override
	public void write(int b) throws IOException {
		os.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		os.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		os.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		os.flush();
	}

	@Override
	public void close() throws IOException {
		os.close();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + os + "]";
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		buf.writeTo(os);
	}
}
