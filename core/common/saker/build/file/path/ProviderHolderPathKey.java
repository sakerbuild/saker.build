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

import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * {@link PathKey} subinterface holding a reference to the associated root file provider.
 * 
 * @see SimpleProviderHolderPathKey
 */
public interface ProviderHolderPathKey extends PathKey {
	/**
	 * The file provider that is associated to the path key.
	 * 
	 * @return The file provider.
	 * @see #getFileProviderKey()
	 */
	@RMICacheResult
	public SakerFileProvider getFileProvider();
}
