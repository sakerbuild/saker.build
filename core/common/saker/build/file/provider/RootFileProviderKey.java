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

import java.util.UUID;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * File provider key that represents a root file provider.
 * <p>
 * Root file providers have no indirection and will access the underlying filesystem directly.
 * <p>
 * There is an {@link UUID} generated automatically for every local root file provider which they are uniquely
 * identified by. This {@link UUID} is usually generated one time for every machine that ever runs the build system. (It
 * is generated the first time {@link LocalFileProvider} is used, and the provider key {@link UUID} is stored in the
 * default storage directory of the build system.)
 * <p>
 * Note that it is not required that all root file provider implementations are backed by {@link LocalFileProvider}.
 * 
 * @see LocalFileProvider
 */
public interface RootFileProviderKey extends FileProviderKey {
	/**
	 * Gets the unique identifier for this root file provider.
	 * 
	 * @return The identifier.
	 */
	@RMICacheResult
	public UUID getUUID();
}
