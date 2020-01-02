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
package saker.build.runtime.execution;

import java.util.Map;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;

public class SimpleRepositoryBuildEnvironment implements RepositoryBuildEnvironment {
	private final SakerEnvironment sakerEnvironment;
	private final ClassLoaderResolverRegistry classLoaderRegistry;
	private final Map<String, String> userParameters;
	private final String identifier;
	private ExecutionPathConfiguration pathConfiguration;

	public SimpleRepositoryBuildEnvironment(SakerEnvironment sakerEnvironment,
			ClassLoaderResolverRegistry classLoaderRegistry, Map<String, String> userParameters,
			ExecutionPathConfiguration pathConfiguration, String identifier) {
		this.sakerEnvironment = sakerEnvironment;
		this.classLoaderRegistry = classLoaderRegistry;
		this.userParameters = userParameters;
		this.pathConfiguration = pathConfiguration;
		this.identifier = identifier;
	}

	@Override
	public SakerEnvironment getSakerEnvironment() {
		return sakerEnvironment;
	}

	@Override
	public ClassLoaderResolverRegistry getClassLoaderResolverRegistry() {
		return classLoaderRegistry;
	}

	@Override
	public Map<String, String> getUserParameters() {
		return userParameters;
	}

	@Override
	public ExecutionPathConfiguration getPathConfiguration() {
		return pathConfiguration;
	}

	@Override
	public String getIdentifier() {
		return identifier;
	}

	public void setPathConfiguration(ExecutionPathConfiguration pathConfiguration) {
		this.pathConfiguration = pathConfiguration;
	}
}