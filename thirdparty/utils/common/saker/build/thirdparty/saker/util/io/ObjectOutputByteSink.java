package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.ObjectOutput;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;

/**
 * Interface extending {@link ObjectOutput} and {@link ByteSink}.
 * <p>
 * The interface is present to have proper RMI annotation for implementations that possibly implement both of the
 * specified interfaces.
 */
public interface ObjectOutputByteSink extends DataOutputByteSink, ObjectOutput {
	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void write(int b) throws IOException {
		DataOutputByteSink.super.write(b);
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void write(byte[] b) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void write(byte[] b, int off, int len) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeObject(Object obj) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void flush() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void close() throws IOException;

}
