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
package saker.build.file.provider;

import java.io.Externalizable;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Interface representing a key object that uniquely identifies a {@link SakerFileProvider} location.
 * <p>
 * Clients can be sure that if two file provider keys {@link #equals(Object) equal}, then they will execute actions on
 * the same files when they are given the same paths as parameters.
 * <p>
 * File provider keys should be serializable, preferably {@link Externalizable}.
 * 
 * @see RootFileProviderKey
 */
public interface FileProviderKey {
	@Override
	@RMICacheResult
	public int hashCode();

	/**
	 * Checks if this file provider key is the same as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	@RMICacheResult
	public String toString();
}
