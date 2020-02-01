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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;

/**
 * Writes the object as serialized data.
 * <p>
 * The given object will be serialized using {@link ObjectOutputStream} and the resulting bytes will be transferred.
 * {@link ObjectInputStream} will be used to read the resulting data on the other side.
 * <p>
 * {@link Enum} instances will be transferred the same way as {@link EnumRMIObjectWriteHandler} would.
 * <p>
 * If the given object is not serializable then {@link RMIObjectTransferFailureException} is thrown.
 * 
 * @see RMISerialize
 * @see RMIObjectOutput#writeSerializedObject(Object)
 * @see ObjectOutputStream
 * @see ObjectInputStream
 */
public final class SerializeRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new SerializeRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#serialize()} instead.
	 */
	public SerializeRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.SERIALIZE;
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