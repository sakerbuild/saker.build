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

import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;

/**
 * Writes the object as an {@link Enum} reference.
 * <p>
 * The given object is transferred as an {@link Enum} denoted by its name through the RMI streams.
 * <p>
 * It is to be noted that any state that the given enum instance has is not transferred, only a reference to its name.
 * <br>
 * Singletons which are backed by enum instances should not use this write handler. <br>
 * Stateless enumerations which have no specific implementation are a good candidate for this.
 * <p>
 * If the given object is not an instance of {@link Enum} then {@link RMIObjectTransferFailureException} is thrown.
 */
public final class EnumRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new EnumRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#enumWriter()} instead.
	 */
	public EnumRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.ENUM;
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