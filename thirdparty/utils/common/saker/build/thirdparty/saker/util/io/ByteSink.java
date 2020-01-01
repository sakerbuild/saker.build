package saker.build.thirdparty.saker.util.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;

/**
 * Interface for handling byte stream output.
 * <p>
 * Byte sink is similar to {@link OutputStream}, but is defined as an interface for allowing stream operations over RMI.
 * RMI solutions can create proxy objects for network transferred objects only for interfaces, therefore it was
 * necessary to declare such an interface that is usable for this use-case.
 * <p>
 * This interface works in similar ways as the {@link OutputStream} class, but is designed to be RMI compatible. The
 * interface also contains extra method(s) for more efficient implementations of common use-cases (like
 * {@link #readFrom(ByteSource)}).
 * <p>
 * To convert between {@link OutputStream} and {@link ByteSink} objects use the static methods declared in this
 * interface.
 * <p>
 * Byte sink implementations are not thread-safe by default.
 * 
 * @see ByteSource
 */
@PublicApi
public interface ByteSink extends Closeable, Flushable {
	/**
	 * Writes a single byte to the byte sink.
	 * <p>
	 * This method works similarly to {@link OutputStream#write(int)}.
	 * 
	 * @param b
	 *            The byte.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void write(int b) throws IOException {
		byte[] buf = { (byte) b };
		write(ByteArrayRegion.wrap(buf));
	}

	/**
	 * Writes the bytes contained in the argument byte array to the byte sink.
	 * <p>
	 * This method works similarly to {@link OutputStream#write(byte[], int, int)}.
	 * 
	 * @param buf
	 *            The bytes to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public void write(ByteArrayRegion buf) throws IOException, NullPointerException;

	/**
	 * Reads bytes from the argument byte source and writes it to <code>this</code> byte sink.
	 * <p>
	 * This method will possibly read all bytes from the argument byte source and all the read bytes will be written to
	 * this byte sink. If the argument is a blocking source, then this method will block too.
	 * <p>
	 * Calling this method instead of copying the bytes externally can have advantages, as implementations can read the
	 * bytes into an internal buffer more efficiently, therefore avoiding unnecessary copying and allocations.
	 * <p>
	 * The default implementation calls {@link ByteSource#writeTo(ByteSink)} of the argument.
	 * 
	 * @param in
	 *            The byte source to read the input from.
	 * @return The number of bytes read and written to this sink.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default long readFrom(ByteSource in) throws IOException, NullPointerException {
		return in.writeTo(this);
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void close() throws IOException {
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void flush() throws IOException {
	}

	/**
	 * Converts the argument {@link OutputStream} to a {@link ByteSink}.
	 * <p>
	 * If the argument is already a byte sink, then it will be returned without modification.
	 * <p>
	 * Else it will be wrapped into a forwarding {@link ByteSink}.
	 * <p>
	 * Closing the result will close the argument too.
	 * 
	 * @param os
	 *            The output stream.
	 * @return The byte sink that uses the passed output stream argument, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static ByteSink valueOf(OutputStream os) {
		if (os == null) {
			return null;
		}
		if (os instanceof ByteSink) {
			return (ByteSink) os;
		}
		return new OutputStreamByteSink(os);
	}

	/**
	 * Converts the argument {@link ObjectOutput} to a {@link ByteSink}.
	 * <p>
	 * If the argument is already a byte sink, then it will be returned without modification.
	 * <p>
	 * Else it will be wrapped into a forwarding {@link ByteSink}.
	 * <p>
	 * Closing the result will close the argument.
	 * 
	 * @param os
	 *            The object output.
	 * @return The byte sink that uses the passed output argument, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static ByteSink valueOf(ObjectOutput os) {
		if (os == null) {
			return null;
		}
		if (os instanceof ByteSink) {
			return (ByteSink) os;
		}
		return new ObjectOutputByteSinkImpl(os);
	}

	/**
	 * Converts the argument {@link ByteSink} to an {@link OutputStream}.
	 * <p>
	 * If the argument is already an {@link OutputStream}, then it will be returned without modification.
	 * <p>
	 * Else it will be wrapped into a forwarding {@link OutputStream}.
	 * <p>
	 * Closing the result will close the argument too.
	 * 
	 * @param sink
	 *            The byte sink.
	 * @return The output stream that uses the passed byte sink argument, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static OutputStream toOutputStream(ByteSink sink) {
		if (sink == null) {
			return null;
		}
		if (sink instanceof OutputStream) {
			return (OutputStream) sink;
		}
		return new ByteSinkOutputStream(sink);
	}
}
