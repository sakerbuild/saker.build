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
package saker.build.ide.support.properties;

import java.util.Map;
import java.util.Set;

public interface IDEProjectProperties {
	public String getWorkingDirectory();

	public String getBuildDirectory();

	public String getMirrorDirectory();

	public Set<? extends RepositoryIDEProperty> getRepositories();

	public Set<? extends Map.Entry<String, String>> getUserParameters();

	public Set<? extends DaemonConnectionIDEProperty> getConnections();

	public Set<? extends ProviderMountIDEProperty> getMounts();

	public String getExecutionDaemonConnectionName();

	public Set<? extends ScriptConfigurationIDEProperty> getScriptConfigurations();

	public Set<String> getScriptModellingExclusions();

	public Set<? extends ParameterizedBuildTargetIDEProperty> getParameterizedBuildTargets();

	/**
	 * Boolean as string value.
	 */
	public String getRequireTaskIDEConfiguration();

	public MountPathIDEProperty getBuildTraceOutput();

	/**
	 * Boolean as string value.
	 */
	public String getBuildTraceEmbedArtifacts();

	/**
	 * Boolean as string value.
	 */
	public String getUseClientsAsClusters();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
