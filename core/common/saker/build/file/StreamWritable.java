package saker.build.file;

import java.io.IOException;
import java.io.OutputStream;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIOutputStreamWrapper;

/**
 * Interface representing content which can be used to write out to a stream.
 */
public interface StreamWritable {
	/**
	 * Writes the contents to the parameter stream.
	 * <p>
	 * The method implementations mustn't close the argument output.
	 * 
	 * @param os
	 *            The stream to write the contents to.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public void writeTo(@RMIWrap(RMIOutputStreamWrapper.class) OutputStream os)
			throws IOException, NullPointerException;

	/**
	 * Writes the contents to the parameter stream.
	 * <p>
	 * The method implementations mustn't close the argument output.
	 * 
	 * @param sink
	 *            The stream to write the contents to.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public default void writeTo(ByteSink sink) throws IOException, NullPointerException {
		writeTo(ByteSink.toOutputStream(sink));
	}
}
