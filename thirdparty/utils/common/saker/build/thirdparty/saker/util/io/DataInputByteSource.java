/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
