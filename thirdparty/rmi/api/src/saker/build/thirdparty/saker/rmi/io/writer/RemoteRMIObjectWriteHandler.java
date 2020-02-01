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
package saker.build.thirdparty.saker.rmi.io.writer;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIRemote;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;

/**
 * Writes the object as a remote proxy to the other endpoint.
 * <p>
 * The given object is transferred to the other endpoint as a remote proxy. Any calls made to the received object will
 * be made as a request through the RMI connection.
 * <p>
 * No specific data is transferred with the object, only a simple pointer to it which can be used to identify the object
 * in the connection.
 * 
 * @see RMIRemote
 * @see RMIObjectOutput#writeRemoteObject(Object)
 */
public final class RemoteRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new RemoteRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#remote()} instead.
	 */
	public RemoteRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.REMOTE;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && this.getClass() == obj.getClass();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}
}