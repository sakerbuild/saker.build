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
package saker.build.util.serial;

import java.io.IOException;

class SerializationProtocolFailedSerializedObject<E> implements SerializedObject<E> {
	private String message;
	private Throwable cause;

	public SerializationProtocolFailedSerializedObject(String message, Throwable cause) {
		this.message = message;
		this.cause = cause;
	}

	public SerializationProtocolFailedSerializedObject(String message) {
		this.message = message;
	}

	@Override
	public E get() throws IOException, ClassNotFoundException {
		throw new SerializationProtocolException(message, cause);
	}
}