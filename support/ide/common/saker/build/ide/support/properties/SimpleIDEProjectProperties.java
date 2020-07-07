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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import saker.build.ide.support.SakerIDEPlugin;
import saker.build.ide.support.SakerIDEProject;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public final class SimpleIDEProjectProperties implements IDEProjectProperties {
	private static final SimpleIDEProjectProperties EMPTY = new SimpleIDEProjectProperties();

	private static final IDEProjectProperties DEFAULTS_INSTANCE;
	static {
		SimpleIDEProjectProperties defaultprops = new SimpleIDEProjectProperties();
		defaultprops.repositories = Collections.singleton(SakerIDEProject.DEFAULT_REPOSITORY_IDE_PROPERTY);
		defaultprops.userParameters = Collections.emptySet();
		defaultprops.connections = Collections.emptySet();
		defaultprops.mounts = Collections.singleton(SakerIDEProject.DEFAULT_MOUNT_IDE_PROPERTY);
		defaultprops.scriptConfigurations = Collections.singleton(SakerIDEProject.DEFAULT_SCRIPT_IDE_PROPERTY);
		defaultprops.workingDirectory = SakerIDEProject.DEFAULT_MOUNT_IDE_PROPERTY.getRoot();
		defaultprops.buildDirectory = SakerIDEProject.DEFAULT_BUILD_DIRECTORY_PATH;
		defaultprops.requireTaskIDEConfiguration = "true";
		DEFAULTS_INSTANCE = defaultprops;
	}

	protected Set<RepositoryIDEProperty> repositories = Collections.emptySet();
	protected Set<? extends Map.Entry<String, String>> userParameters = Collections.emptySet();
	protected Set<DaemonConnectionIDEProperty> connections = Collections.emptySet();
	protected Set<ProviderMountIDEProperty> mounts = Collections.emptySet();
	protected Set<ScriptConfigurationIDEProperty> scriptConfigurations = Collections.emptySet();
	protected Set<String> scriptModellingExclusions = Collections.emptySet();

	protected String workingDirectory;
	protected String buildDirectory;
	protected String mirrorDirectory;
	protected String executionDaemonConnectionName;

	protected String requireTaskIDEConfiguration;

	protected MountPathIDEProperty buildTraceOutput;
	protected String buildTraceEmbedArtifacts;

	public static IDEProjectProperties empty() {
		return EMPTY;
	}

	public static IDEProjectProperties copy(IDEProjectProperties copy) {
		return new SimpleIDEProjectProperties(copy);
	}

	public static IDEProjectProperties getDefaultsInstance() {
		return DEFAULTS_INSTANCE;
	}

	private SimpleIDEProjectProperties() {
	}

	private SimpleIDEProjectProperties(IDEProjectProperties copy) {
		this.repositories = ImmutableUtils.makeImmutableLinkedHashSet(copy.getRepositories());
		this.userParameters = SakerIDEPlugin.makeImmutableEntrySet(copy.getUserParameters());
		this.connections = ImmutableUtils.makeImmutableLinkedHashSet(copy.getConnections());
		this.mounts = ImmutableUtils.makeImmutableLinkedHashSet(copy.getMounts());
		this.scriptConfigurations = ImmutableUtils.makeImmutableLinkedHashSet(copy.getScriptConfigurations());
		this.scriptModellingExclusions = ImmutableUtils.makeImmutableNavigableSet(copy.getScriptModellingExclusions());

		this.workingDirectory = copy.getWorkingDirectory();
		this.buildDirectory = copy.getBuildDirectory();
		this.mirrorDirectory = copy.getMirrorDirectory();
		this.executionDaemonConnectionName = copy.getExecutionDaemonConnectionName();
		this.requireTaskIDEConfiguration = copy.getRequireTaskIDEConfiguration();
		this.buildTraceOutput = copy.getBuildTraceOutput();
		this.buildTraceEmbedArtifacts = copy.getBuildTraceEmbedArtifacts();
	}

	@Override
	public String getWorkingDirectory() {
		return workingDirectory;
	}

	@Override
	public String getBuildDirectory() {
		return buildDirectory;
	}

	@Override
	public String getMirrorDirectory() {
		return mirrorDirectory;
	}

	@Override
	public Set<RepositoryIDEProperty> getRepositories() {
		return repositories;
	}

	@Override
	public Set<? extends Map.Entry<String, String>> getUserParameters() {
		return userParameters;
	}

	@Override
	public Set<DaemonConnectionIDEProperty> getConnections() {
		return connections;
	}

	@Override
	public Set<ProviderMountIDEProperty> getMounts() {
		return mounts;
	}

	@Override
	public String getExecutionDaemonConnectionName() {
		return executionDaemonConnectionName;
	}

	@Override
	public Set<ScriptConfigurationIDEProperty> getScriptConfigurations() {
		return scriptConfigurations;
	}

	@Override
	public Set<String> getScriptModellingExclusions() {
		return scriptModellingExclusions;
	}

	@Override
	public String getRequireTaskIDEConfiguration() {
		return requireTaskIDEConfiguration;
	}

	@Override
	public MountPathIDEProperty getBuildTraceOutput() {
		return buildTraceOutput;
	}

	@Override
	public String getBuildTraceEmbedArtifacts() {
		return buildTraceEmbedArtifacts;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildDirectory == null) ? 0 : buildDirectory.hashCode());
		result = prime * result + ((buildTraceEmbedArtifacts == null) ? 0 : buildTraceEmbedArtifacts.hashCode());
		result = prime * result + ((buildTraceOutput == null) ? 0 : buildTraceOutput.hashCode());
		result = prime * result + ((connections == null) ? 0 : connections.hashCode());
		result = prime * result
				+ ((executionDaemonConnectionName == null) ? 0 : executionDaemonConnectionName.hashCode());
		result = prime * result + ((mirrorDirectory == null) ? 0 : mirrorDirectory.hashCode());
		result = prime * result + ((mounts == null) ? 0 : mounts.hashCode());
		result = prime * result + ((repositories == null) ? 0 : repositories.hashCode());
		result = prime * result + ((requireTaskIDEConfiguration == null) ? 0 : requireTaskIDEConfiguration.hashCode());
		result = prime * result + ((scriptConfigurations == null) ? 0 : scriptConfigurations.hashCode());
		result = prime * result + ((scriptModellingExclusions == null) ? 0 : scriptModellingExclusions.hashCode());
		result = prime * result + ((userParameters == null) ? 0 : userParameters.hashCode());
		result = prime * result + ((workingDirectory == null) ? 0 : workingDirectory.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleIDEProjectProperties other = (SimpleIDEProjectProperties) obj;
		if (buildDirectory == null) {
			if (other.buildDirectory != null)
				return false;
		} else if (!buildDirectory.equals(other.buildDirectory))
			return false;
		if (buildTraceEmbedArtifacts == null) {
			if (other.buildTraceEmbedArtifacts != null)
				return false;
		} else if (!buildTraceEmbedArtifacts.equals(other.buildTraceEmbedArtifacts))
			return false;
		if (buildTraceOutput == null) {
			if (other.buildTraceOutput != null)
				return false;
		} else if (!buildTraceOutput.equals(other.buildTraceOutput))
			return false;
		if (connections == null) {
			if (other.connections != null)
				return false;
		} else if (!connections.equals(other.connections))
			return false;
		if (executionDaemonConnectionName == null) {
			if (other.executionDaemonConnectionName != null)
				return false;
		} else if (!executionDaemonConnectionName.equals(other.executionDaemonConnectionName))
			return false;
		if (mirrorDirectory == null) {
			if (other.mirrorDirectory != null)
				return false;
		} else if (!mirrorDirectory.equals(other.mirrorDirectory))
			return false;
		if (mounts == null) {
			if (other.mounts != null)
				return false;
		} else if (!mounts.equals(other.mounts))
			return false;
		if (repositories == null) {
			if (other.repositories != null)
				return false;
		} else if (!repositories.equals(other.repositories))
			return false;
		if (requireTaskIDEConfiguration == null) {
			if (other.requireTaskIDEConfiguration != null)
				return false;
		} else if (!requireTaskIDEConfiguration.equals(other.requireTaskIDEConfiguration))
			return false;
		if (scriptConfigurations == null) {
			if (other.scriptConfigurations != null)
				return false;
		} else if (!scriptConfigurations.equals(other.scriptConfigurations))
			return false;
		if (scriptModellingExclusions == null) {
			if (other.scriptModellingExclusions != null)
				return false;
		} else if (!scriptModellingExclusions.equals(other.scriptModellingExclusions))
			return false;
		if (userParameters == null) {
			if (other.userParameters != null)
				return false;
		} else if (!userParameters.equals(other.userParameters))
			return false;
		if (workingDirectory == null) {
			if (other.workingDirectory != null)
				return false;
		} else if (!workingDirectory.equals(other.workingDirectory))
			return false;
		return true;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(IDEProjectProperties copy) throws NullPointerException {
		Objects.requireNonNull(copy, "copy");
		return new Builder(copy);
	}

	public static final class Builder {
		private SimpleIDEProjectProperties result;

		public Builder() {
			result = new SimpleIDEProjectProperties();
		}

		public Builder(IDEProjectProperties copy) {
			result = new SimpleIDEProjectProperties(copy);
		}

		public Builder setRepositories(Set<? extends RepositoryIDEProperty> repositories) {
			result.repositories = repositories == null ? Collections.emptySet()
					: ImmutableUtils.makeImmutableLinkedHashSet(repositories);
			return this;
		}

		public Builder setUserParameters(Set<? extends Map.Entry<String, String>> userParameters) {
			result.userParameters = userParameters == null ? Collections.emptySet()
					: SakerIDEPlugin.makeImmutableEntrySet(userParameters);
			return this;
		}

		public Builder setConnections(Set<? extends DaemonConnectionIDEProperty> connections) {
			result.connections = connections == null ? Collections.emptySet()
					: ImmutableUtils.makeImmutableLinkedHashSet(connections);
			return this;
		}

		public Builder setMounts(Set<? extends ProviderMountIDEProperty> mounts) {
			result.mounts = mounts == null ? Collections.emptySet() : ImmutableUtils.makeImmutableLinkedHashSet(mounts);
			return this;
		}

		public Builder setScriptConfigurations(Set<? extends ScriptConfigurationIDEProperty> scriptConfigurations) {
			result.scriptConfigurations = scriptConfigurations == null ? Collections.emptySet()
					: ImmutableUtils.makeImmutableLinkedHashSet(scriptConfigurations);
			return this;
		}

		public Builder setWorkingDirectory(String workingDirectory) {
			result.workingDirectory = workingDirectory;
			return this;
		}

		public Builder setBuildDirectory(String buildDirectory) {
			result.buildDirectory = buildDirectory;
			return this;
		}

		public Builder setMirrorDirectory(String mirrorDirectory) {
			result.mirrorDirectory = mirrorDirectory;
			return this;
		}

		public Builder setExecutionDaemonConnectionName(String executionDaemonConnectionName) {
			result.executionDaemonConnectionName = executionDaemonConnectionName;
			return this;
		}

		public Builder setScriptModellingExclusions(Set<String> scriptModellingExclusions) {
			result.scriptModellingExclusions = scriptModellingExclusions == null ? Collections.emptySet()
					: ImmutableUtils.makeImmutableNavigableSet(scriptModellingExclusions);
			return this;
		}

		public Builder setRequireTaskIDEConfiguration(String requireTaskIDEConfiguration) {
			result.requireTaskIDEConfiguration = requireTaskIDEConfiguration;
			return this;
		}

		public Builder setRequireTaskIDEConfiguration(boolean requireTaskIDEConfiguration) {
			return this.setRequireTaskIDEConfiguration(Boolean.toString(requireTaskIDEConfiguration));
		}

		public Builder setBuildTraceOutput(MountPathIDEProperty buildTraceOutput) {
			result.buildTraceOutput = buildTraceOutput;
			return this;
		}

		public Builder setBuildTraceEmbedArtifacts(String buildTraceEmbedArtifacts) {
			result.buildTraceEmbedArtifacts = buildTraceEmbedArtifacts;
			return this;
		}

		public Builder setBuildTraceEmbedArtifacts(boolean buildTraceEmbedArtifacts) {
			return this.setBuildTraceEmbedArtifacts(Boolean.toString(buildTraceEmbedArtifacts));
		}

		public IDEProjectProperties build() {
			SimpleIDEProjectProperties res = this.result;
			if (res == null) {
				throw new IllegalStateException("Builder already used.");
			}
			this.result = null;
			return res;
		}

		public IDEProjectProperties buildReuse() {
			return new SimpleIDEProjectProperties(this.result);
		}
	}

}