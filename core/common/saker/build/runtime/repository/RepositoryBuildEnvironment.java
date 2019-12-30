package saker.build.runtime.repository;

import java.util.Map;

import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.environment.SakerEnvironment;
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
	 * Gets the identifier which was used for this repository.
	 * <p>
	 * The repository identifier is specified by the user to reference the repository by a specific name. If an
	 * identifier was not set by the user configuration, an automatically generated one is used.
	 * 
	 * @return The repository identifier.
	 */
	public String getIdentifier();
}
