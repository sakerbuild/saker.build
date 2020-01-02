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
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * {@link RMIWrapper} implementation which creates a delegate file on the receiving endpoint.
 */
@PublicApi
public class DelegatingSakerFileRMIWrapper implements RMIWrapper {
	private SakerFile file;

	/**
	 * Creates a new instance.
	 * <p>
	 * This method is usually only called by the RMI runtime.
	 */
	public DelegatingSakerFileRMIWrapper() {
	}

	/**
	 * Creates a new instance for the given file.
	 * <p>
	 * This method is usually only called by the RMI runtime.
	 * 
	 * @param file
	 *            The file.
	 */
	public DelegatingSakerFileRMIWrapper(SakerFile file) {
		this.file = file;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeRemoteObject(file);
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		SakerFile readfile = (SakerFile) in.readObject();
		this.file = new DelegateSakerFile(readfile);
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
