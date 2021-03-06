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
package testing.saker.build.tests.script.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import saker.build.file.path.WildcardPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.scripting.model.ScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class TestScriptModellingEnvironmentConfiguration implements ScriptModellingEnvironmentConfiguration {
	private ExecutionPathConfiguration pathConfiguration;
	private ExecutionScriptConfiguration scriptConfiguration;
	private Collection<? extends ExternalScriptInformationProvider> externalScriptInformationProviders = Collections
			.emptyList();
	private Map<String, String> userParameters = Collections.emptyMap();
	private Set<? extends WildcardPath> excludedScriptPaths = Collections.emptySet();

	public TestScriptModellingEnvironmentConfiguration(ExecutionPathConfiguration pathConfiguration,
			ExecutionScriptConfiguration scriptConfiguration) {
		this.pathConfiguration = pathConfiguration;
		this.scriptConfiguration = scriptConfiguration;
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
		return externalScriptInformationProviders;
	}

	@Override
	public Map<String, String> getUserParameters() {
		return userParameters;
	}

	public void setExcludedScriptPaths(Set<? extends WildcardPath> excludedScriptPaths) {
		this.excludedScriptPaths = excludedScriptPaths;
	}

	public void setExternalScriptInformationProviders(
			Collection<? extends ExternalScriptInformationProvider> externalScriptInformationProviders) {
		this.externalScriptInformationProviders = externalScriptInformationProviders;
	}

	public void setUserParameters(Map<String, String> userParameters) {
		this.userParameters = userParameters == null ? Collections.emptyMap()
				: ImmutableUtils.makeImmutableLinkedHashMap(userParameters);
	}
}
