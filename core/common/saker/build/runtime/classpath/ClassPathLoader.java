package saker.build.runtime.classpath;

import java.io.IOException;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;

/**
 * Interface for loading classpath objects to a specified location.
 * <p>
 * Classpath loaders are required to load implementation dependent classpath files to a given file system location. They
 * are stateful objects, as they can be optinally queried if the previously loaded classpath state have changed.
 * <p>
 * Implementations can implement caching functionality to avoid loading the same classpath to the same location multiple
 * times.
 */
public interface ClassPathLoader {
	/**
	 * Loads this classpath to the given directory.
	 * <p>
	 * The result of this operation can be a directory containing the classes in the respective package subdirectories,
	 * or a JAR file containing the classes to be loaded.
	 * <p>
	 * The operation can use appropriate caching not to load the same contents over and over again.
	 * <p>
	 * Subclasses are responsible to copy/download/replicate every resource necessary for the classpath to operate.
	 * Meaning that all class files and resources on the classpath should be present after this operation so that no
	 * class loading error occurs. Note that additional resources which are dynamically linked to these classes are not
	 * required to be present after this operation completes, they can and should be placed on-demand to a determined
	 * storage directory. (This directory can vary based on classpath use-cases.)
	 * 
	 * @param directory
	 *            The directory to load the classpath to.
	 * @return The relative path from the parameter directory to the loaded classpath classes.
	 * @throws IOException
	 *             If the operation failed.
	 */
	public SakerPath loadTo(ProviderHolderPathKey directory) throws IOException;

	/**
	 * Checks if the classpath have changed at the location specified by this loader.
	 * <p>
	 * Implementations should determine if the classpath which this loader loads has changed or not. If this method
	 * returns <code>true</code>, the load manager should attempt to reload the classpath by calling
	 * {@link #loadTo(ProviderHolderPathKey)} again (but not required to do so).
	 * <p>
	 * This method is generally useful when developing classpath implementations. A classpath is loaded by the build
	 * system, and a build is executed using it. The user might decide to replace the classpath implementation, by
	 * developing it further. The previously loaded classpath was cached by the build system, so it needs to determine
	 * if it has changed. By calling this method, it can see that it has changed, and attempt to reload the new
	 * implementation. <br>
	 * Note: Implementing this method will not guarantee that any classpath is reloaded. If multiple clients use the
	 * same classpath, it won't be reloaded, as they all need to be closed first.
	 * <p>
	 * The default implementation returns <code>false</code>.
	 * 
	 * @return <code>true</code> if the classpath should be reloaded.
	 */
	public default boolean isChanged() {
		return false;
	}
}
