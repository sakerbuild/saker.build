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

import java.util.Map;

import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;

/**
 * Interface representing the environment available for {@link BuildRepository build repositories}.
 * <p>
 * An instance of this interface is passed to the build repositories during instantiation. They can operate based on the
 * configuration data available in this interface.
 * <p>
 * Build repositories are required to set the class lookup resolution appropriately in order for proper incremental
 * build handling. (See {@link #getClassLoaderResolverRegistry()})
 * <p>
 * The result of getter methods in this interface are considered to return the same objects, <b>unless otherwise
 * noted</b>.
 */
public interface RepositoryBuildEnvironment {
	/**
	 * Gets the core build environment for the repository to use.
	 * 
	 * @return The build environment.
	 */
	public SakerEnvironment getSakerEnvironment();

	/**
	 * Gets the registry for class loader resolvers which is used during build execution.
	 * <p>
	 * Build repositories are required to set the class lookup resolution appropriately in order for proper incremental
	 * build handling.
	 * <p>
	 * The registry is used during serialization of objects during builds. This is prevalent when the incremental state
	 * of the build execution is persisted, and during builds with clusters.
	 * <p>
	 * If the associated build repository fails to set up the class resolution, then it will result in failures during
	 * serialization of build results, using clusters will fail, or generally incremental builds will most likely not
	 * work.
	 * <p>
	 * Build repositories are required to modify the class resolution appropriately, when
	 * {@linkplain BuildRepository#handleChanges(Object) change detection} results in modifications.
	 * <p>
	 * Build repositories are required to unregister their installed resolvers when they are
	 * {@linkplain BuildRepository#close() closed}.
	 * 
	 * @return The registry.
	 */
	public ClassLoaderResolverRegistry getClassLoaderResolverRegistry();

	/**
	 * Gets the arbitrary parameters which were specified by the user for the build execution.
	 * 
	 * @return An unmodifiable map of user parameters.
	 */
	public Map<String, String> getUserParameters();

	/**
	 * Gets the path configuration used by the build execution.
	 * <p>
	 * This method may return different path configuration objects when the build system cache state is invalidated or
	 * rebuilt in some ways. However, even if different path configurations are returned, they can only differ in
	 * backing file provider objects, and they will not be different configurations.
	 * <p>
	 * Such scenarios can happen when connections to remote {@linkplain SakerFileProvider file providers} are broken up
	 * and then reestablished later. In these cases using the previously returned file providers will most likely result
	 * in RMI errors.
	 * <p>
	 * Callers are recommended <b>not</b> to cache any file providers acquired from this configuration.
	 * <p>
	 * While this method can return different objects, it will not return different objects while a build execution is
	 * running. In general, clients only need to deal with this in {@link BuildRepository#detectChanges()} and related
	 * methods.
	 * 
	 * @return The path configuration.
	 */
	public ExecutionPathConfiguration getPathConfiguration();

	/**
	 * Gets the local file provider of the build execution.
	 * <p>
	 * The method returns the file provider for the build execution machine. That is the one that coordinates the build
	 * and runs it.
	 * <p>
	 * This method can be useful for repositories that were initialized on build clusters and need to access files on
	 * the coordinator machine.
	 * 
	 * @return The coordinator file provider.
	 * @since saker.build 0.8.15
	 * @see ExecutionContext#getLocalFileProvider()
	 */
	public SakerFileProvider getLocalFileProvider();

	/**
	 * Modifies a shared object for the build repository.
	 * <p>
	 * Sets the shared object for the given key, or removes it if the value is <code>null</code>. This method can be
	 * called on the coordinator machine.
	 * <p>
	 * See {@link #getSharedObject(Object)} for detailed explanation.
	 * 
	 * @param key
	 *            The key of the shared object.
	 * @param value
	 *            The value of the shared object.
	 * @throws NullPointerException
	 *             If the key is <code>null</code>.
	 * @throws UnsupportedOperationException
	 *             If this method is called on a remote build cluster. ({@link #isRemoteCluster()} returns
	 *             <code>true</code>.)
	 * @since saker.build 0.8.15
	 * @see #isRemoteCluster()
	 */
	public default void setSharedObject(Object key, Object value)
			throws NullPointerException, UnsupportedOperationException {
		//not supported on clusters
		throw new UnsupportedOperationException();
	}

	/**
	 * Gets a shared object for the build repository.
	 * <p>
	 * Shared objects are used to convey some build repository specific information to the remote build clusters. Shared
	 * objects can be set on the coordinator machine and they can be retrieved on all build machines that take part of
	 * the build execution.
	 * <p>
	 * When a shared object is set, the build repositories that are initialized on remote build clusters can retrieve
	 * these objects and modify their behaviour based on them. They can also be used to communicate with the build
	 * repository on the coordinator machine.
	 * <p>
	 * Generally shared objects are used to ensure that build repositories are properly loaded on the build clusters in
	 * the same way as on the coordinator.
	 * <p>
	 * Shared objects are private to each loaded repository. Shared objects cannot be retrieved for other repositories
	 * of the build execution.
	 * <p>
	 * If a shared object is transferred as a remote object, then callers should take care when calling remote methods.
	 * The connection may be lost between the build cluster and the coordinator at any time. Remote methods shouldn't be
	 * called when the {@linkplain BuildRepository#close() build repository is closing} as the RMI connection may've
	 * been broken up by then. <br>
	 * You may also need to requery the remote shared objects in {@link BuildRepository#detectChanges()} as if the
	 * remote repository is reloaded, RMI calls may fail with incompatible class errors.
	 * 
	 * @param key
	 *            The key of the shared object.
	 * @return The value of for the shared object or <code>null</code> if not set.
	 * @throws NullPointerException
	 *             If the key is <code>null</code>.
	 * @since saker.build 0.8.15
	 * @see #isRemoteCluster()
	 */
	public Object getSharedObject(Object key) throws NullPointerException;

	/**
	 * Gets if the build repository was initialized to be act as part of a build cluster.
	 * <p>
	 * This method returns <code>true</code> if and only if this build repository was intialized in order to take part
	 * of a build cluster execution.
	 * <p>
	 * If a build repository is initialized as a cluster, {@link #setSharedObject(Object, Object) setSharedObject} will
	 * always throw an {@link UnsupportedOperationException}.
	 * 
	 * @return <code>true</code> if the environment is part of a build cluster.
	 * @since saker.build 0.8.15
	 */
	public boolean isRemoteCluster();

	/**
	 * Gets the identifier which was used for this repository.
	 * <p>
	 * The repository identifier is specified by the user to reference the repository by a specific name. If an
	 * identifier was not set by the user configuration, an automatically generated one is used.
	 * 
	 * @return The repository identifier.
	 */
	public String getIdentifier();
}
