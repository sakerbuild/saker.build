package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ByteSink} and {@link OutputStream} implementation that dynamically chooses the actual output for each write
 * operation.
 * <p>
 * This class forwards its write operations to a {@link ByteSink} that is choosen using the given function at
 * construction time. If a write opperation is requested, the function is called, which can choose the stream to execute
 * the write operation on. If the function returns <code>null</code>, the fallback {@link ByteSink} will be used to
 * complete the operation.
 * <p>
 * The chooser function can decide arbitrarily, but an useful implementation is that it returns a stream based on some
 * thread local state.
 * <p>
 * Closing this stream will <b>not</b> close the fallback sink, or any choosen outputs by the chooser function.
 * 
 * @see DynamicFilterByteSource
 */
public class DynamicFilterByteSink extends OutputStream implements ByteSink {
	private final Supplier<? extends ByteSink> sinkProvider;
	private final ByteSink fallbackSink;

	/**
	 * Creates a new instance.
	 * 
	 * @param sinkProvider
	 *            The sink provider function which is called when write operations are issued.
	 * @param fallbackSink
	 *            The fallback sink which is used when the sink provider fails to get an output.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public DynamicFilterByteSink(Supplier<? extends ByteSink> sinkProvider, ByteSink fallbackSink)
			throws NullPointerException {
		Objects.requireNonNull(sinkProvider, "sink provider");
		Objects.requireNonNull(fallbackSink, "fallback sink");
		this.sinkProvider = sinkProvider;
		this.fallbackSink = fallbackSink;
	}

	/**
	 * Gets the fallback byte sink that is used when the sink chooser function fails to find an output.
	 * 
	 * @return The fallback sink.
	 */
	public ByteSink getFallbackSink() {
		return fallbackSink;
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		getCurrentOutput().write(buf);
	}

	@Override
	public void write(int b) throws IOException {
		getCurrentOutput().write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		getCurrentOutput().write(ByteArrayRegion.wrap(b));
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		getCurrentOutput().write(ByteArrayRegion.wrap(b, off, len));
	}

	@Override
	public void flush() throws IOException {
		getCurrentOutput().flush();
	}

	@Override
	public void close() throws IOException {
		// do not close the stream
	}

	private ByteSink getCurrentOutput() {
		ByteSink current = sinkProvider.get();
		if (current != null) {
			return current;
		}
		return fallbackSink;
	}

}
