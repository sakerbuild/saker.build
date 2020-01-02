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

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.util.cache.CacheKey;

/**
 * A build repository is a view to its enclosing {@link SakerRepository} configured for a specific build execution.
 * <p>
 * As differently configured executions might require different repository behaviours, it is necessary to have a
 * repostiory interface which has the same lifetime as the execution itself. The {@link BuildRepository} interface
 * serves the purpose of this.
 * <p>
 * Build repositories are responsible for holding data that can be configured using user defined build parameters. Based
 * on these parameters the repositories can work in an execution specific way that is independent from other executions.
 * <p>
 * Build repositories can be created using their enclosing repositories, with a specific
 * {@linkplain RepositoryBuildEnvironment environment} configured for the current use-case. (See
 * {@link SakerRepository#createBuildRepository(RepositoryBuildEnvironment)})
 * <p>
 * The build repositories are responsible for looking up tasks that can be used during execution. The tasks are named
 * using the {@link TaskName} class. The build repository can choose how they wish to interpret these names.
 * <p>
 * The lifecycle of a build repository depends on how the build system wishes to handle it. It can be longer than a
 * single build execution, and they can even exist without even one build running with it.
 * <p>
 * The lifetime of a build repository is the following:
 * <ol>
 * <li>Instantiation by {@link SakerRepository#createBuildRepository(RepositoryBuildEnvironment)}</li>
 * <li>A build execution is started.</li>
 * <li>The build repository is used during the execution.</li>
 * <li>The build execution is finished.</li>
 * <li>{@link #close()} is called on the build repository.</li>
 * </ol>
 * The steps 2-4 can occur zero, one, or multiple times during its lifetime. The build repositories should be usable
 * even when they are not being part of an execution, as IDE related features might be required to enhance user
 * experience.
 * <p>
 * Repositories can be cached to improve load performance for executions. When they are cached, {@link #detectChanges()}
 * will be called to detect any changes to the repository which would cause it to require reloading. See the
 * {@linkplain #detectChanges() method documentation} for more information.
 * <p>
 * The build repositories can provide dynamic information about its contents for IDE usage. It is strongly recommended
 * (but optional) that build repositories implement at least basic support for providing these informations via
 * {@link #getScriptInformationProvider()}.
 * <p>
 * Build repositories should not access the {@link SakerEnvironment} caching functionality asynchronously. Meaning, that
 * a build repository should not use {@linkplain SakerEnvironment#getCachedData(CacheKey) cache keys} and
 * {@link SakerEnvironment#getEnvironmentPropertyCurrentValue(EnvironmentProperty) environment properties} when they're
 * not called during a build execution. Using it may lead unexpected {@linkplain LinkageError linkage errors} when
 * closing the repositories.
 */
public interface BuildRepository extends AutoCloseable {
	/**
	 * Searches the corresponding task for the specified name.
	 * <p>
	 * Looks up the task in this repository for the given name. An exception thrown with an explanation if the task
	 * cannot be found in this repository.
	 * <p>
	 * The lookup algorithm is implementation dependent for each repository.
	 * <p>
	 * Repository implementations are not required to return the same instances for the same name if this method is
	 * called consecutively.
	 * 
	 * @param taskname
	 *            The name of the task to look up.
	 * @return The found task for the given name.
	 * @throws TaskNotFoundException
	 *             If the task was not found.
	 */
	public TaskFactory<?> lookupTask(TaskName taskname) throws TaskNotFoundException;

	/**
	 * Gets the information provider for this repository that can be used to provide development assistance to the user.
	 * 
	 * @return The script information provider.
	 */
	public default ExternalScriptInformationProvider getScriptInformationProvider() {
		return null;
	}

	/**
	 * Detects any changes in the underlying repository that occurred since the last execution finished.
	 * <p>
	 * Build repositories can be cached between executions to improve startup time for subsequent builds. Given the fact
	 * that repositories are shared objects, their underlying resources can be changed externally by external processes.
	 * In order to keep a valid state of cache between executions it is necessary to determine any changes that occurred
	 * before the next build execution.
	 * <p>
	 * Implementations of build repositories should detect any possible changes in the repository resources that can
	 * require reloading of cached resources. (E.g. classes, chached data, etc...)
	 * <p>
	 * Implementations of this method should take the possible file provider object changes described in
	 * {@link RepositoryBuildEnvironment#getPathConfiguration()} into account.
	 * <p>
	 * Example: <br>
	 * A repository implementation is backed by JARs to look up the tasks by name. A build repository is created, and an
	 * execution is run with it. During this execution, the repository loads a task implementation from the JAR
	 * <i>task.jar</i>. The build finishes successfully, and the build system caches this repository for future re-use.
	 * <br>
	 * During two consecutive executions, some external agents decide to modify this <i>task.jar</i> which contains the
	 * task used by the execution. <br>
	 * As the next incremental build execution starts, this build repository will be re-used by the build system. It
	 * will call {@link #detectChanges()} to determine if any resources for the repository has changed. The
	 * implementation should detect, that <i>task.jar</i> was modified externally, and return non-<code>null</code> from
	 * this method to signal the changes. <br>
	 * The cache will later call {@link #handleChanges(Object)} with the previously returned object to handle the
	 * detected changes. If the repository fails to handle the changes, then it should throw an appropriate
	 * {@link RuntimeException}. This exception will be reported to the user and the next execution will fail to start.
	 * <p>
	 * If this method throws an exception, then all of the repository related cached data will be cleared/closed, and a
	 * completely new build repository instance will be created for the next execution.
	 * <p>
	 * The change detection is separated into two phases in order to avoid possible runtime errors after closing some
	 * classpath related to repositories. After calling {@link #detectChanges()}, the execution will clear cached data
	 * in the build environment, and later call {@link #handleChanges(Object)} in order to actually handle the detected
	 * changes. If the build system didn't separate the two phases, then clearing the cached data from the environment
	 * could result in {@link NoClassDefFoundError} or other related {@link LinkageError LinkageErrors} when such code
	 * is invoked which has its classpath already closed.
	 * <p>
	 * It is possible that after {@link #detectChanges()} returning non-<code>null</code>, the
	 * {@link #handleChanges(Object)} won't be called. This might be due to other repositories throwing exceptions and
	 * prematurely aborting the execution. To avoid errors related to this, implementations shouldn't lock resources in
	 * this method.
	 * <p>
	 * Note: A repository implementation can decide that it doesn't implement change detection. This means that it will
	 * be harder for clients of the repository to implement plugins for it (if possible), and users will need to
	 * manually clear the build environment cache. This can result in slower incremental builds.
	 * <p>
	 * In general, changes in repositories should occur rarely, unless specific development is done. It can happen often
	 * if one is developing a plugin for a repository, but should happen rarely (preferably never) if one is just using
	 * the services provided by a repository.
	 * 
	 * @return Non-<code>null</code> if any changes were detected.
	 */
	public default Object detectChanges() {
		return null;
	}

	/**
	 * Handles the previously detected changes in {@link #detectChanges()}.
	 * <p>
	 * This method is only called if {@link #detectChanges()} prevously returned non-<code>null</code>.
	 * <p>
	 * Implementations should handle the previously detected changes. They can throw an appropriate
	 * {@link RuntimeException} to signal any errors during handling the changes. Throwing an exception from this method
	 * will cause the build execution to abort.
	 * 
	 * @param detectedchanges
	 *            The previous return value from {@link #detectChanges()}.
	 */
	public default void handleChanges(Object detectedchanges) {
	}

	/**
	 * Closes this build repository.
	 * <p>
	 * Calling this method signals that the build repository is not going to be used anymore. Implementations can
	 * release any resource related to this build repository, however they are not required to do so, and are allowed to
	 * keep resources cached in the enclosing {@link SakerRepository} parent. Closing of the parent repository should
	 * release those resources nonetheless.
	 * <p>
	 * Build repositories are required to unregister their installed class resolvers when they are closed. (See
	 * {@link RepositoryBuildEnvironment#getClassLoaderResolverRegistry()})
	 * <p>
	 * Calling this method again after the first call should be a no-op. I.e. this method should be idempotent.
	 * 
	 * @throws IOException
	 *             In case of any error.
	 */
	@Override
	public void close() throws IOException;
}
