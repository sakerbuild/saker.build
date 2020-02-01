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

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * {@link RMIWrapper} implementation that write an object as serializable.
 * 
 * @see RMIObjectOutput#writeSerializedObject(Object)
 */
public class RMISerializableWrapper implements RMIWrapper {
	private Object object;

	/**
	 * Creates a new instance.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 */
	public RMISerializableWrapper() {
	}

	/**
	 * Creates a new instance for an object.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param object
	 *            The object.
	 */
	public RMISerializableWrapper(Object object) {
		this.object = object;
	}

	@Override
	public Object getWrappedObject() {
		return object;
	}

	@Override
	public Object resolveWrapped() {
		return object;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeSerializedObject(object);
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		this.object = in.readObject();
	}
}
