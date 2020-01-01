package saker.build.thirdparty.saker.util.io;

import java.io.DataInput;
import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;

/**
 * Interface extending {@link DataInput} and {@link ByteSource}.
 * <p>
 * The interface is present to have proper RMI annotation for implementations that possibly implement both of the
 * specified interfaces.
 */
public interface DataInputByteSource extends ByteSource, DataInput {

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void readFully(byte[] b) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void readFully(byte[] b, int off, int len) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int skipBytes(int n) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public boolean readBoolean() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public byte readByte() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int readUnsignedByte() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public short readShort() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int readUnsignedShort() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public char readChar() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public int readInt() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public long readLong() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public float readFloat() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public double readDouble() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public String readLine() throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public String readUTF() throws IOException;

}
