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
package saker.build.file;

import java.io.IOException;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * {@link RMIWrapper} implementation for handling files transferred during remote execution.
 * <p>
 * This RMI wrapper will call {@link SakerFile#getRemoteExecutionRMIWrapper()} and try to write the file with the result
 * wrapper. If that method returns <code>null</code> the the file will be transferred by creating a remote delegate to
 * it (by using {@link DelegatingSakerFileRMIWrapper}).
 */
@PublicApi
public class RemoteExecutionSakerFileRMIWrapper implements RMIWrapper {
	private SakerFile file;

	/**
	 * Creates a new instance.
	 * <p>
	 * This method is usually only called by the RMI runtime.
	 */
	public RemoteExecutionSakerFileRMIWrapper() {
	}

	/**
	 * Creates a new instance for the given file.
	 * <p>
	 * This method is usually only called by the RMI runtime.
	 * 
	 * @param file
	 *            The file.
	 * @throws IllegalArgumentException
	 *             If the file is a remote object.
	 */
	public RemoteExecutionSakerFileRMIWrapper(SakerFile file) throws IllegalArgumentException {
		if (RMIConnection.isRemoteObject(file)) {
			throw new IllegalArgumentException("Cannot RMI wrap a remote file object.");
		}
		this.file = file;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		Class<? extends RMIWrapper> wrapperclass = file.getRemoteExecutionRMIWrapper();
		if (wrapperclass != null) {
			out.writeWrappedObject(file, wrapperclass);
		} else {
			//will read the file as a delegate
			out.writeWrappedObject(file, DelegatingSakerFileRMIWrapper.class);
		}
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		file = (SakerFile) in.readObject();
	}

	@Override
	public Object resolveWrapped() {
		return file;
	}

	@Override
	public Object getWrappedObject() {
		throw new UnsupportedOperationException();
	}

}
