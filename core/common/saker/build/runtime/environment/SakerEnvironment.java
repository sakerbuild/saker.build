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
package saker.build.runtime.environment;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.util.cache.CacheKey;

/**
 * Core environment for the build system.
 * <p>
 * Tasks can access features of the build system which outlive the time of a build execution.
 * <p>
 * The environment provides features for querying properties, caching data between executions, and is parameterizable
 * using user parameters.
 * <p>
 * The lifetime of the environment outlives the build executions.
 * <p>
 * The environmenmt always resides on the machine which is used to execute the task. This is important to keep in mind
 * when designing tasks for remote execution.
 */
public interface SakerEnvironment {
	/**
	 * Gets the current value of an environment property.
	 * <p>
	 * Environment properties are cached and only calculated once for each property that
	 * {@link EnvironmentProperty#equals(Object) equals}.
	 * <p>
	 * Any exception thrown during property calculation is relayed to the caller.
	 * 
	 * @param <T>
	 *            The type of the returned property.
	 * @param environmentproperty
	 *            The property.
	 * @return The current value of the property.
	 * @throws NullPointerException
	 *             If the property is <code>null</code>.
	 * @throws PropertyComputationFailedException
	 *             If the argument property throws an exception during the computation of its value. The thrown
	 *             exception is available through the {@linkplain PropertyComputationFailedException#getCause() cause}.
	 */
	public <T> T getEnvironmentPropertyCurrentValue(EnvironmentProperty<T> environmentproperty)
			throws NullPointerException, PropertyComputationFailedException;

	/**
	 * Gets a cached data from the environment.
	 * <p>
	 * The build system manages a cache for the keys. Each key uniquely identifies a cache entry, and it is used to
	 * calculate the cached data. The cache stores objects in a resource-data pair, which is used for proper
	 * deallocation of the resources. More information in the documentation of {@link CacheKey}.
	 * 
	 * @param <DataType>
	 *            The type of the data the cache key generates.
	 * @param key
	 *            The cache key to identify and compute the data.
	 * @return The computed cached data.
	 * @throws Exception
	 *             Any exception thrown during the allocation of the data.
	 */
	public <DataType> DataType getCachedData(CacheKey<DataType, ?> key) throws Exception;

	/**
	 * Gets the object which is used to manage the classpath loading for the build environment.
	 * <p>
	 * Clients should only use this when caching data via {@link #getCachedData(CacheKey)}, or make sure that clients
	 * always unload the loaded class path. (The manager uses garbage collection for loaded classpaths, but it is
	 * non-deterministic, and should not be relied upon.)
	 * 
	 * @return The classpath loading manager.
	 */
	public ClassPathLoadManager getClassPathManager();

	/**
	 * Gets the user parameters which were used to instantiate this environment.
	 * <p>
	 * This is <b>not</b> the same user parameters used during build execution.
	 * <p>
	 * The map contains arbitrarily specified key-value pairs by the user. Values may be <code>null</code>.
	 * 
	 * @return An unmodifiable map of user parameters.
	 */
	public Map<String, String> getUserParameters();

	/**
	 * Gets the base thread group for the environment.
	 * <p>
	 * Callers can use this threadgroup to start threads which outlives the lifetime of an execution. They should
	 * <b>not</b> start threads arbitrarily, but only when they are caching data via {@link #getCachedData(CacheKey)}.
	 * <p>
	 * If tasks need to start threads for cached data, they are required to start these on the environment thread group.
	 * If they don't, then an exception will be thrown when the corresponding build execution finishes. The build
	 * environment may call {@link ThreadGroup#destroy()} on the build execution thread group to ensure that task
	 * executions do not leak threads. If any alive threads remain in the build execution thread group, the build may be
	 * considered failed.
	 * <p>
	 * This thread group will be destroyed when the build environment is closed.
	 * 
	 * @return The base thread group.
	 */
	public ThreadGroup getEnvironmentThreadGroup();

	/**
	 * Gets the path to the JAR which was used to load the build system related classes.
	 * <p>
	 * For example this JAR can be used to start external processes which require the build system classes on their
	 * classpath.
	 * 
	 * @return The path to the JAR for the build system.
	 */
	public Path getEnvironmentJarPath();

	/**
	 * Gets the unique identifier of this environment.
	 * <p>
	 * Each environment that exists have an unique {@link UUID} identifier. They are automatically generated when the
	 * environment is constructed. Callers should not rely on these identifiers being the same between two build
	 * executions. The identifiers may change between different invocations of a build.
	 * <p>
	 * The identifier can be used in some build system features to uniquely identify environments to run operations on.
	 * <p>
	 * This method may throw {@link UnsupportedOperationException} when it is called in an inappropriate time. Currently
	 * the only one is when the suitability of an environment is being determined by tasks to execute on. See
	 * {@link TaskExecutionEnvironmentSelector} for more information.
	 * 
	 * @return The unique identifier of this environment.
	 * @throws UnsupportedOperationException
	 *             If the method is being called in an inappropriate time.
	 */
	public UUID getEnvironmentIdentifier() throws UnsupportedOperationException;
}
