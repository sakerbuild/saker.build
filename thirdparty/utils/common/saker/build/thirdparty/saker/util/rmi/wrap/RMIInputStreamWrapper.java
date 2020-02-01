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
package saker.build.thirdparty.saker.util.rmi.wrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link RMIWrapper} implementation that transfers an {@link InputStream} by writing a {@link ByteSource} as a remote
 * object instead, and converts it back to an {@link InputStream} during reading.
 * <p>
 * The input stream will be wrapped into a {@link ByteSource}, and that will be written as a remote object to the other
 * endpoint. <br>
 * The remote {@link ByteSource} will be read, and converted back to an {@link InputStream}.
 * <p>
 * Writing the resulting {@link InputStream} back to the original endpoint will not return the original
 * {@link InputStream} instance to it, but a stream that is forwarded through the remote endpoint. This will probably
 * heavily degrade the performance, so if this scenario is possible to happen, users should choose a different
 * transferring mechanism.
 * 
 * @see ByteSource#valueOf(InputStream)
 * @see ByteSource#toInputStream(ByteSource)
 */
public class RMIInputStreamWrapper implements RMIWrapper {
	private InputStream stream;

	/**
	 * Creates a new instance.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 */
	public RMIInputStreamWrapper() {
	}

	/**
	 * Creates a new instance for the given input stream.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param is
	 *            The input stream.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public RMIInputStreamWrapper(InputStream is) throws NullPointerException {
		Objects.requireNonNull(is, "input stream");
		this.stream = is;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeRemoteObject(ByteSource.valueOf(stream));
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		stream = ByteSource.toInputStream((ByteSource) in.readObject());
	}

	@Override
	public Object getWrappedObject() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object resolveWrapped() {
		return stream;
	}

}
