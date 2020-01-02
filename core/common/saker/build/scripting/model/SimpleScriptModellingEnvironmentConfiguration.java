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
