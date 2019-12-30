package saker.build.scripting;

import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.TargetConfiguration;

/**
 * Simple data class implementation of the {@link TargetConfigurationReadingResult} interface.
 */
@PublicApi
public class SimpleTargetConfigurationReadingResult implements TargetConfigurationReadingResult {
	private TargetConfiguration targetConfiguration;
	private ScriptInformationProvider informationProvider;

	/**
	 * Creates a new instance with the given arguments.
	 * 
	 * @param targetConfiguration
	 *            The target configuration.
	 * @param informationProvider
	 *            The script information provider.
	 * @throws NullPointerException
	 *             If the target configuration is <code>null</code>.
	 */
	public SimpleTargetConfigurationReadingResult(TargetConfiguration targetConfiguration,
			ScriptInformationProvider informationProvider) throws NullPointerException {
		Objects.requireNonNull(targetConfiguration, "target configuration");

		this.targetConfiguration = targetConfiguration;
		this.informationProvider = informationProvider;
	}

	@Override
	public TargetConfiguration getTargetConfiguration() {
		return targetConfiguration;
	}

	@Override
	public ScriptInformationProvider getInformationProvider() {
		return informationProvider;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (targetConfiguration != null ? "targetConfiguration=" + targetConfiguration + ", " : "")
				+ (informationProvider != null ? "informationProvider=" + informationProvider : "") + "]";
	}

}
