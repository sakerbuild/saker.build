package saker.build.scripting.model;

import java.io.IOException;
import java.util.NavigableSet;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;

/**
 * Configureable environment for script modelling.
 * <p>
 * The script modelling environment is responsible for discovering the script file subjects for modelling, and serving
 * as a common container for build script models.
 * <p>
 * The currently discovered script file paths can be retrieved by calling {@link #getTrackedScriptPaths()}. The
 * discovery mechanism is implementation dependent for the build scripts. (E.g. it can be implemented by file system
 * watchers, or can require manual handling)
 * <p>
 * The behaviour for creating models by the environment is implementation dependent. It can use lazy or eager
 * instantiation as it sees fit.
 * <p>
 * The configuration of the modelling environment can change during its lifetime, and clients should handle that
 * gracefully according to the recommended behaviour suggested by {@link #getConfiguration()}.
 */
public interface ScriptModellingEnvironment extends AutoCloseable {
	/**
	 * Gets a snapshot of tracked script paths by this environment.
	 * <p>
	 * The returned collection is a snapshot, after calling this method the environment might have discovered new script
	 * files.
	 * 
	 * @return A snapshot of the tracked script paths. Might be unmodifiable. If not, then modifications do not
	 *             propagate back.
	 */
	public NavigableSet<SakerPath> getTrackedScriptPaths();

	/**
	 * Gets a script model for the file at the given path.
	 * <p>
	 * This method doesn't always return non-<code>null</code>, some possible scenarios are the following:
	 * <ul>
	 * <li>No script configuration was defined for the file at the given path.</li>
	 * <li>The path is configured to be {@link ScriptModellingEnvironmentConfiguration#getExcludedScriptPaths()
	 * excluded}.</li>
	 * <li>The environment failed to load the scripting language for the file.</li>
	 * <li>The path is not a valid path for the current
	 * {@link ScriptModellingEnvironmentConfiguration#getPathConfiguration() path configuration}.</li>
	 * </ul>
	 * This method will return non-<code>null</code> if the file doesn't exist, but the
	 * {@link ScriptSyntaxModel#createModel(IOSupplier) creation} of the model might fail due to the file not being
	 * found.
	 * 
	 * @param scriptpath
	 *            The path of the file.
	 * @return The model for the file at the given path or <code>null</code> if not applicable.
	 * @throws InvalidPathFormatException
	 *             If the script path is not absolute.
	 */
	public ScriptSyntaxModel getModel(SakerPath scriptpath) throws InvalidPathFormatException;

	/**
	 * Gets the configuration for this modelling environment.
	 * <p>
	 * The configuration can change during the lifetime of the modelling environment without any notification to the
	 * callers. Clients should not retain reference to the returned configuration instance, but query it every time from
	 * the modelling environment if access to related data is required.
	 * 
	 * @return The configuration.
	 */
	public ScriptModellingEnvironmentConfiguration getConfiguration();

	/**
	 * Closes this modelling environment.
	 * <p>
	 * Closing will release all resources of this modelling environment. Further calls to this environment are to return
	 * empty collections and <code>null</code> when appropriate.
	 * <p>
	 * Subsequent calls to this method are no-op. I.e. this method is idempotent.
	 * 
	 * @throws IOException
	 *             In case of closing error.
	 */
	@Override
	public void close() throws IOException;
}
