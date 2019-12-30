package saker.build.scripting.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import saker.build.file.path.WildcardPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class SimpleScriptModellingEnvironmentConfiguration implements ScriptModellingEnvironmentConfiguration {
	private ExecutionPathConfiguration pathConfiguration;
	private ExecutionScriptConfiguration scriptConfiguration;
	private Collection<? extends ExternalScriptInformationProvider> externalInformationProviders;
	private Map<String, String> userParameters;
	private Set<? extends WildcardPath> excludedScriptPaths;

	public SimpleScriptModellingEnvironmentConfiguration(ExecutionPathConfiguration pathConfiguration,
			ExecutionScriptConfiguration scriptConfiguration, Set<? extends WildcardPath> excludedScriptPaths,
			Collection<? extends ExternalScriptInformationProvider> externalInformationProviders,
			Map<String, String> userParameters) {
		this.pathConfiguration = pathConfiguration;
		this.scriptConfiguration = scriptConfiguration;
		this.excludedScriptPaths = ImmutableUtils.makeImmutableNavigableSet(excludedScriptPaths);
		this.externalInformationProviders = ImmutableUtils.makeImmutableList(externalInformationProviders);
		this.userParameters = ImmutableUtils.makeImmutableLinkedHashMap(userParameters);
	}

	@Override
	public ExecutionPathConfiguration getPathConfiguration() {
		return pathConfiguration;
	}

	@Override
	public ExecutionScriptConfiguration getScriptConfiguration() {
		return scriptConfiguration;
	}

	@Override
	public Set<? extends WildcardPath> getExcludedScriptPaths() {
		return excludedScriptPaths;
	}

	@Override
	public Collection<? extends ExternalScriptInformationProvider> getExternalScriptInformationProviders() {
		return externalInformationProviders;
	}

	@Override
	public Map<String, String> getUserParameters() {
		return userParameters;
	}
}
