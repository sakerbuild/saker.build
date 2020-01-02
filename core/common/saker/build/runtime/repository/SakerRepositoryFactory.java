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
package saker.build.runtime.repository;

import java.util.ServiceLoader;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Stateless factory class for instantiation of {@link SakerRepository repositories}.
 * <p>
 * Implementations of this class should have a public no-arg constructor.
 * <p>
 * This class might by used by the {@link ServiceLoader} mechanism.
 */
public interface SakerRepositoryFactory {
	/**
	 * Instantiates a repository for the given repository environment.
	 * 
	 * @param environment
	 *            The environemnt for the repository to use.
	 * @return The instantiated repository.
	 */
	@RMIForbidden
	public SakerRepository create(RepositoryEnvironment environment);
}
