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

import java.io.Externalizable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import saker.build.thirdparty.saker.util.io.function.ObjectReaderFunction;

final class SerializedTypeObject<T> implements SerializedObject<Class<T>> {
	private final Class<T> type;

	private Constructor<? extends Externalizable> externalizableConstructor;

	// if equals to SerializedTypeObject.class then the value hasn't been calculated yet
	private Object customSerializableReader = SerializedTypeObject.class;

	public SerializedTypeObject(Class<T> type) {
		this.type = type;
	}

	@SuppressWarnings("unchecked")
	public Constructor<? extends Externalizable> getExternalizableConstructor()
			throws NoSuchMethodException, SecurityException {
		Constructor<? extends Externalizable> extconstructor = externalizableConstructor;
		if (extconstructor != null) {
			return externalizableConstructor;
		}
		extconstructor = (Constructor<? extends Externalizable>) type.getDeclaredConstructor();
		//set accessible just in case
		extconstructor.setAccessible(true);
		this.externalizableConstructor = extconstructor;
		return extconstructor;
	}

	public Externalizable newExternalizableInstance() throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		return getExternalizableConstructor().newInstance();
	}

	@SuppressWarnings("unchecked")
	public ObjectReaderFunction<ContentReaderObjectInput, Object> getCustomSerializableReader() {
		Object reader = this.customSerializableReader;
		if (reader != SerializedTypeObject.class) {
			return (ObjectReaderFunction<ContentReaderObjectInput, Object>) reader;
		}
		reader = ContentWriterObjectOutput.SERIALIZABLE_CLASS_READERS.get(type);
		this.customSerializableReader = reader;
		return (ObjectReaderFunction<ContentReaderObjectInput, Object>) reader;
	}

	@Override
	public Class<T> get() {
		return type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SerializedTypeObject<?> other = (SerializedTypeObject<?>) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(getClass().getSimpleName());
		builder.append("[type=");
		builder.append(type);
		builder.append("]");
		return builder.toString();
	}

}
