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

import java.io.IOException;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Stateless interface which represent an unique location of a classpath.
 * <p>
 * Classpath locations uniquely specify the location of a classpath that is to be loaded. Implementations should adhere
 * to the {@link #equals(Object)} and {@link #hashCode()} contract.
 * <p>
 * Two classpath locations are equal if they will load themselves in the same manner.
 * <p>
 * Each classpath location has an identifier which should uniquely identify the location. It is recommended that it is a
 * hash of some input, or other uniquely identifying function is performed to derive it.
 * <p>
 * It is strongly recommended that classpath locations are RMI transferrable.
 * 
 * @see ClassPathLoader#loadTo(ProviderHolderPathKey)
 */
public interface ClassPathLocation {
	/**
	 * Creates a new classpath loader which is able to load the specified classpath to a given location.
	 * 
	 * @return The created classpath loader.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ClassPathLoader getLoader() throws IOException;

	/**
	 * Gets the unique identifier for the classpath location.
	 * <p>
	 * The identifiers should be reasonably short. They should be representable in the file system as file names,
	 * meaning they should not contain any special character that a file name cannot contain. They may include the slash
	 * (<code>'/'</code>) character.
	 * <p>
	 * It is not required that classpath locations with the same identifier equal, but if they equal, they must return
	 * the same identifiers.
	 * <p>
	 * Identifiers are usually used by external managers to determine file system storage locations. If they include
	 * slashes, usually subdirectories are created accordingly.
	 * 
	 * @return The identifier for this classpath location.
	 */
	@RMICacheResult
	public String getIdentifier();

	@Override
	public int hashCode();

	/**
	 * Checks if this object represents the same classpath location as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}