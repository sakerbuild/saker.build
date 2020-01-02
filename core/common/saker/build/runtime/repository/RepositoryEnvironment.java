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

import java.nio.file.Path;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.runtime.classpath.ClassPathLoader;
import saker.build.runtime.environment.SakerEnvironment;

/**
 * Interface specifying the basic working environment configuration for {@linkplain SakerRepository repositories}.
 * <p>
 * This interface contains the file system locations where the repositories can store their data.
 * <p>
 * The result of getter methods in this interface are considered to return the same objects, <b>unless otherwise
 * noted</b>.
 */
public interface RepositoryEnvironment {
	/**
	 * Gets the local path to the classes of the repository;
	 * <p>
	 * The returned path points to a file or folder on the local machine where the classes of the repository are
	 * located. The classes can be contained in a directory tree with appropriate package directories or in a JAR file.
	 * 
	 * @return The classpath of the repository.
	 */
	public Path getRepositoryClassPath();

	/**
	 * Gets the local path to the load directory of the classpath.
	 * <p>
	 * The returned path is a directory which was used to load the repository classes to.
	 * 
	 * @return The directory target of the classpath loading.
	 * @see ClassPathLoader#loadTo(ProviderHolderPathKey)
	 */
	public Path getRepositoryClassPathLoadDirectory();

	/**
	 * Gets the local directory path where the repository can store its runtime data.
	 * <p>
	 * The directory is private to the currently loaded repository classpath. If multiple repositories are contained in
	 * a single classpath, then they share the repository storage directory.
	 * 
	 * @return The storage directory path.
	 */
	public Path getRepositoryStorageDirectory();

	/**
	 * Gets the common storage directory this environment was initialized with.
	 * <p>
	 * The returned directory is a base directory for all environment related data. Any other storage directories are
	 * probably but not necessarily under this base directory.
	 * 
	 * @return The common storage directory path.
	 */
	public Path getEnvironmentStorageDirectory();

	/**
	 * Gets the path to the JAR which was used to load the build system related classes.
	 * <p>
	 * For example this JAR can be used to start external processes which require the build system classes on their
	 * classpath.
	 * 
	 * @return The path to the JAR for the build system.
	 * @see SakerEnvironment#getEnvironmentJarPath()
	 */
	public Path getEnvironmentJarPath();
}
