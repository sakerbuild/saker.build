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

import java.io.IOException;
import java.util.Arrays;

import saker.build.task.TaskName;

/**
 * Interface for repositories which are the main extension points for the build system.
 * <p>
 * Repositories are loaded and used by the build environment to provide the appropriate services. They currently provide
 * two functionalities:
 * <ul>
 * <li>Dynamically loading build tasks to use (See {@link BuildRepository#lookupTask(TaskName)} )</li>
 * <li>Execution actions (See {@link #executeAction(String...)} )</li>
 * </ul>
 * A view to the repositories can be configured to provide services configured for build executions. This is necessary
 * as different build configurations might need the same repository to act in different ways. (See
 * {@link #createBuildRepository(RepositoryBuildEnvironment)})
 * <p>
 * Repositories can optionally provide access to actions which basically act as a main function for the repository. It
 * can be used to execute code with the given parameters. Via actions, repositories can provide access to core features
 * of it without the need of intializing a build execution.
 * <p>
 * Repositories are instantiated using {@link SakerRepositoryFactory} factory class that receives the environment for
 * repositories to work with.
 */
public interface SakerRepository extends AutoCloseable {
	/**
	 * Creates a build repository for the configuration specified by the environment parameter.
	 * <p>
	 * The created build repository should operate based on the configuration available from the parameter environment.
	 * <p>
	 * Build repositories are required to set the class lookup resolution appropriately in order for proper incremental
	 * build handling. (See {@link RepositoryBuildEnvironment#getClassLoaderResolverRegistry()})
	 * <p>
	 * Repository implementations should keep track of the created build repositories, and call {@link #close()} on them
	 * if <code>this</code> parent repository is closed. If the created build repositories handle no unmanaged data,
	 * this behaviour can be omitted.
	 * <p>
	 * If this method throws an exception, the build execution will be aborted.
	 * 
	 * @param environment
	 *            The build repository environment to use.
	 * @return The created build repository.
	 */
	public BuildRepository createBuildRepository(RepositoryBuildEnvironment environment);

	/**
	 * Executes an arbitrary action on this repository with the given parameters.
	 * <p>
	 * This method servers as a main method to the repository. Calling it will execute custom actions specified by the
	 * repository implementation.
	 * <p>
	 * Implementations are not recommended to call {@link System#exit(int)} after finishing the action, but they are
	 * allowed to do so. Make sure to document exit related behaviour for the callers.
	 * <p>
	 * The default implementation throws an {@link UnsupportedOperationException}.
	 * 
	 * @param arguments
	 *            The arguments for the action.
	 * @throws Exception
	 *             In case of any error occurring during execution of the action.
	 * @throws UnsupportedOperationException
	 *             If this repository action is not supported.
	 */
	public default void executeAction(String... arguments) throws Exception, UnsupportedOperationException {
		throw new UnsupportedOperationException("Action not supported with arguments: " + Arrays.toString(arguments));
	}

	/**
	 * Closes the repository and any child build repositories.
	 * <p>
	 * Subsequent calls to this method should be a no-op. I.e. this method should be idempotent.
	 * 
	 * @throws IOException
	 *             In case of closing error.
	 */
	@Override
	public void close() throws IOException;
}
