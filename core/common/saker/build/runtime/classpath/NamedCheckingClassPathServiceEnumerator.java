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
package saker.build.runtime.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;

/**
 * {@link ClassPathServiceEnumerator} implementation for locating a service for a class name and expected type.
 * <p>
 * The implementation will throw a {@link ClassPathEnumerationError} if the retrieved class is not an instance of the
 * expected type.
 * 
 * @param <T>
 *            The type of the requested service.
 */
@PublicApi
public class NamedCheckingClassPathServiceEnumerator<T> extends NamedClassPathServiceEnumerator<T> {
	private static final long serialVersionUID = 1L;

	private transient Class<?> expectedInstanceOf;

	/**
	 * For {@link Externalizable}.
	 */
	public NamedCheckingClassPathServiceEnumerator() {
	}

	/**
	 * Creates a new enumerator for the given class name and expected type.
	 * 
	 * @param className
	 *            The class name of the service to look up.
	 * @param expectedInstanceOf
	 *            The expected type to check the service for.
	 */
	public NamedCheckingClassPathServiceEnumerator(String className, Class<?> expectedInstanceOf) {
		super(className);
		this.expectedInstanceOf = expectedInstanceOf;
	}

	@Override
	protected Iterable<? extends T> createIterable(Class<?> found) {
		if (!expectedInstanceOf.isAssignableFrom(found)) {
			throw new ClassPathEnumerationError(
					"Service class: " + found + " is not assignable to " + expectedInstanceOf);
		}
		return super.createIterable(found);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(expectedInstanceOf);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		expectedInstanceOf = (Class<? super T>) in.readObject();
	}

}
