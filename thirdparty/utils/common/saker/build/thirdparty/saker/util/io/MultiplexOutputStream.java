package saker.build.thirdparty.saker.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * Output stream implementation that delegates the calls to it to multiple subject output streams.
 * <p>
 * This class works in a similar way to {@link FilterOutputStream}, but instead of filtering for one output stream, this
 * class forwards calls to all of its subjects.
 * <p>
 * If a stream throws an {@link IOException} for a given call, the other streams still going to be called for that
 * operation, and the exception will be propagated to the caller after all of the stream calls finished.
 * <p>
 * This class closes the output streams it was constructed with.
 * <p>
 * This stream class is not thread safe.
 */
public class MultiplexOutputStream extends OutputStream implements ByteSink {
	private Collection<? extends OutputStream> outputs;

	/**
	 * Creates a new instance without any subject streams.
	 * <p>
	 * The calls to this output stream will do nothing.
	 */
	protected MultiplexOutputStream() {
		outputs = Collections.emptySet();
	}

	/**
	 * Creates a new instance for the given streams.
	 * <p>
	 * If the argument contains any <code>null</code> values, the stream may throw a {@link NullPointerException} later.
	 * <p>
	 * Any modifications to the argument array will not propagate back to this instance.
	 * 
	 * @param streams
	 *            The streams.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public MultiplexOutputStream(OutputStream... streams) throws NullPointerException {
		Objects.requireNonNull(streams, "streams");
		outputs = ImmutableUtils.makeImmutableList(streams);
	}

	/**
	 * Creates a new instance for the given streams.
	 * <p>
	 * If the argument contains any <code>null</code> values, the stream may throw a {@link NullPointerException} later.
	 * <p>
	 * Any modifications to the argument collection will not propagate back to this instance.
	 * 
	 * @param streams
	 *            The streams.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public MultiplexOutputStream(Collection<? extends OutputStream> streams) throws NullPointerException {
		Objects.requireNonNull(streams, "streams");
		outputs = ImmutableUtils.makeImmutableList(streams);
	}

	@Override
	public void write(int b) throws IOException {
		IOUtils.foreach(outputs, o -> o.write(b));
	}

	@Override
	public void write(byte[] b) throws IOException {
		IOUtils.foreach(outputs, o -> o.write(b));
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		IOUtils.foreach(outputs, o -> o.write(b, off, len));
	}

	@Override
	public void flush() throws IOException {
		IOUtils.foreach(outputs, OutputStream::flush);
	}

	@Override
	public void close() throws IOException {
		IOUtils.close(outputs);
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		buf.writeTo((OutputStream) this);
	}

}
