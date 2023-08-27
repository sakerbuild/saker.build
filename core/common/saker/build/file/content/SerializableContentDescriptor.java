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
package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.util.data.annotation.ValueType;

/**
 * Content descriptor that is compared by equality and is backed by a custom serializable object.
 * <p>
 * The underlying object should be serializable in order for proper build system operation. It is strongly recommended
 * to be {@link Externalizable} as well.
 */
@PublicApi
@ValueType
public class SerializableContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The object that this content descriptor is backed by.
	 */
	protected Object object;

	/**
	 * For {@link Externalizable}.
	 */
	public SerializableContentDescriptor() {
	}

	/**
	 * Creates a new instance with the given object.
	 * 
	 * @param object
	 *            The object.
	 */
	public SerializableContentDescriptor(Object object) {
		this.object = object;
	}

	/**
	 * Gets the object that this content descriptor is backed by.
	 * 
	 * @return The object. (may be <code>null</code>)
	 */
	public Object getObject() {
		return object;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(object);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		object = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
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
		SerializableContentDescriptor other = (SerializableContentDescriptor) obj;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + object + "]";
	}

}
