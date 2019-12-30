package saker.build.scripting.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;

/**
 * Configuration collection for {@link ScriptModellingEnvironment}.
 * <p>
 * The congfiguration objects in this interface are the same as if a build execution would be started with the same
 * configuration.
 * 
 * @see ScriptModellingEnvironment#getConfiguration()
 */
public interface ScriptModellingEnvironmentConfiguration {
	/**
	 * Gets the path configuration for the modelling environment.
	 * 
	 * @return The path configuration.
	 */
	public ExecutionPathConfiguration getPathConfiguration();

	/**
	 * Gets the script configuration for the modelling environment.
	 * 
	 * @return The script configuration.
	 */
	public ExecutionScriptConfiguration getScriptConfiguration();

	/**
	 * Gets a set of wildcards paths which are used to exclude the matching build scripts from the modelling
	 * environment.
	 * <p>
	 * Matching build scripts will not be part of the modelling, and no {@link ScriptSyntaxModel} object will be
	 * instantiated for them. In general, the matched scripts will not be presented to the user in the IDE.
	 * 
	 * @return An unmodifiable set of wildcard paths.
	 */
	public Set<? extends WildcardPath> getExcludedScriptPaths();

	/**
	 * Gets the external script information providers available for modelling information.
	 * 
	 * @return An unmodifiable collection of information providers.
	 */
	public Collection<? extends ExternalScriptInformationProvider> getExternalScriptInformationProviders();

	/**
	 * Gets the user parameters for the build execution.
	 * 
	 * @return An unmodifiable map of user parameters.
	 * @see ExecutionContext#getUserParameters()
	 */
	public Map<String, String> getUserParameters();
}
