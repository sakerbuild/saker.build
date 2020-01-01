package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.ObjectInput;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;

/**
 * Interface extending {@link ObjectInput} and {@link ByteSource}.
 * <p>
 * The interface is present to have proper RMI annotation for implementations that possibly implement both of the
 * specified interfaces.
 */
public interface ObjectInputByteSource extends DataInputByteSource, ObjectInput {

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public Object readObject() throws ClassNotFoundException, IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int read(byte[] b) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int read(byte[] b, int off, int len) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int available() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default int read() throws IOException {
		return DataInputByteSource.super.read();
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default long skip(long n) throws IOException {
		return DataInputByteSource.super.skip(n);
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void close() throws IOException;

}
