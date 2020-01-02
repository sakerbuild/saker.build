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
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Interface for enumerating services in a classpath.
 * <p>
 * Implementations take a {@link ClassLoader} as an argument, and create an {@link Iterable} for listing the available
 * services that this enumerator looks up.
 * <p>
 * The methods of looking up the services is implementation dependent.
 * <p>
 * The {@link #getServices(ClassLoader)} and any of the returned {@link Iterable} methods may only throw
 * {@link ClassPathEnumerationError} to signal that accessing the requested resources failed.
 * <p>
 * Implementations should adhere to the {@link #equals(Object)} and {@link #hashCode()} contract.
 * <p>
 * Implementations should be {@link Externalizable}.
 * <p>
 * The justification of this interface is to customize the loading mechanism of services, instead of only relying on
 * {@link ServiceLoader}.
 * 
 * @param <T>
 *            The type of the enumerated service.
 * @see ClassPathEnumerationError
 * @see ServiceLoaderClassPathServiceEnumerator
 * @see NamedClassPathServiceEnumerator
 */
public interface ClassPathServiceEnumerator<T> {
	/**
	 * Gets an {@link Iterable} to the located services by this enumerator.
	 * <p>
	 * The returned iterable is able to list the services that this enumerator should list.
	 * <p>
	 * This method, and any method of the returned {@link Iterable} may throw {@link ClassPathEnumerationError} to
	 * signal that the enumeration failed. (It is similar to {@link ServiceConfigurationError})
	 * <p>
	 * The returned {@link Iterable} may be lazily populated.
	 * 
	 * @param classloader
	 *            The classloader to use to lookup services.
	 * @return The iterable for the services.
	 * @throws ClassPathEnumerationError
	 *             In case of enumeration error.
	 */
	public Iterable<? extends T> getServices(ClassLoader classloader) throws ClassPathEnumerationError;

	@Override
	public int hashCode();

	/**
	 * Checks if this enumerator will list the same services as the parameter if they are given the same
	 * {@link ClassLoader} instance.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}
