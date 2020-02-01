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

import java.io.DataOutput;
import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;

/**
 * Interface extending {@link DataOutput} and {@link ByteSink}.
 * <p>
 * The interface is present to have proper RMI annotation for implementations that possibly implement both of the
 * specified interfaces.
 */
public interface DataOutputByteSink extends ByteSink, DataOutput {
	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void write(int b) throws IOException {
		ByteSink.super.write(b);
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void write(byte[] b) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void write(byte[] b, int off, int len) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeBoolean(boolean v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeByte(int v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeShort(int v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeChar(int v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeInt(int v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeLong(long v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeFloat(float v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeDouble(double v) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeBytes(String s) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeChars(String s) throws IOException;

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public void writeUTF(String s) throws IOException;
}
