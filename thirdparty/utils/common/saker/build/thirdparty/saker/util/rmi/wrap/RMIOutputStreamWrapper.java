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
import java.io.OutputStream;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteSink;

/**
 * {@link RMIWrapper} implementation that transfers an {@link OutputStream} by writing a {@link ByteSink} as a remote
 * object instead, and converts it back to an {@link OutputStream} during reading.
 * <p>
 * The output stream will be wrapped into a {@link ByteSink}, and that will be written as a remote object to the other
 * endpoint. <br>
 * The remote {@link ByteSink} will be read, and converted back to an {@link OutputStream}.
 * <p>
 * Writing the resulting {@link OutputStream} back to the original endpoint will not return the original
 * {@link OutputStream} instance to it, but a stream that is forwarded through the remote endpoint. This will probably
 * heavily degrade the performance, so if this scenario is possible to happen, users should choose a different
 * transferring mechanism.
 * 
 * @see ByteSink#valueOf(OutputStream)
 * @see ByteSink#toOutputStream(ByteSink)
 */
public class RMIOutputStreamWrapper implements RMIWrapper {
	private OutputStream stream;

	/**
	 * Creates a new instance.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 */
	public RMIOutputStreamWrapper() {
	}

	/**
	 * Creates a new instance for the given output stream.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param os
	 *            The output stream.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public RMIOutputStreamWrapper(OutputStream os) throws NullPointerException {
		Objects.requireNonNull(os, "output stream");
		this.stream = os;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeRemoteObject(ByteSink.valueOf(stream));
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		stream = ByteSink.toOutputStream((ByteSink) in.readObject());
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
