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
