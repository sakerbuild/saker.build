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
package saker.build.file.path;

import saker.build.file.provider.RootFileProviderKey;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Path key is a path-file provider key pair which uniquely identifies a file location.
 * <p>
 * If two path keys {@link #equals(Object) equal}, then the client can be sure that they locate the same file given they
 * are used with their corresponding file providers.
 * <p>
 * Path keys are immutable, its getter functions return the same instances if called multiple times.
 * <p>
 * The interface does not require that the path is actually valid to the corresponding file provider.
 * 
 * @see ProviderHolderPathKey
 * @see SimplePathKey
 */
public interface PathKey {
	/**
	 * Gets the path of the file location.
	 * 
	 * @return The path.
	 */
	@RMICacheResult
	public SakerPath getPath();

	/**
	 * Gets the root file provider key that is associated with the path.
	 * 
	 * @return The root file provider.
	 */
	@RMISerialize
	@RMICacheResult
	public RootFileProviderKey getFileProviderKey();

	/**
	 * The hash code for a path key consists only of the hash code of the {@linkplain #getPath() path}.
	 * 
	 * <pre>
	 * return getPath().hashCode();
	 * </pre>
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	@RMICacheResult
	public int hashCode();

	/**
	 * Checks if this path key represents the same file location as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	@RMICacheResult
	public String toString();
}
