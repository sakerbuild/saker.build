package saker.build.ide.support.properties;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import saker.build.ide.support.SakerIDEPlugin;
import saker.build.ide.support.SakerIDEProject;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

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
		defaultprops.requireTaskIDEConfiguration = true;
		DEFAULTS_INSTANCE = defaultprops;
	}

	protected Set<RepositoryIDEProperty> repositories;
	protected Set<? extends Map.Entry<String, String>> userParameters;
	protected Set<DaemonConnectionIDEProperty> connections;
	protected Set<ProviderMountIDEProperty> mounts;
	protected Set<ScriptConfigurationIDEProperty> scriptConfigurations;
	protected Set<String> scriptModellingExclusions;

	protected String workingDirectory;
	protected String buildDirectory;
	protected String mirrorDirectory;
	protected String executionDaemonConnectionName;

	protected boolean requireTaskIDEConfiguration;

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
		this.repositories = ObjectUtils.cloneLinkedHashSet(copy.getRepositories());
		this.userParameters = SakerIDEPlugin.unmodifiablizeEntrySet(copy.getUserParameters());
		this.connections = ObjectUtils.cloneLinkedHashSet(copy.getConnections());
		this.mounts = ObjectUtils.cloneLinkedHashSet(copy.getMounts());
		this.scriptConfigurations = ObjectUtils.cloneLinkedHashSet(copy.getScriptConfigurations());
		this.scriptModellingExclusions = ObjectUtils.cloneTreeSet(copy.getScriptModellingExclusions());

		this.workingDirectory = copy.getWorkingDirectory();
		this.buildDirectory = copy.getBuildDirectory();
		this.mirrorDirectory = copy.getMirrorDirectory();
		this.executionDaemonConnectionName = copy.getExecutionDaemonConnectionName();
		this.requireTaskIDEConfiguration = copy.isRequireTaskIDEConfiguration();
	}

	private final void unmodifiablize() {
		this.repositories = ImmutableUtils.unmodifiableSet(this.repositories);
		this.userParameters = ImmutableUtils.unmodifiableSet(this.userParameters);
		this.connections = ImmutableUtils.unmodifiableSet(this.connections);
		this.mounts = ImmutableUtils.unmodifiableSet(this.mounts);
		this.scriptConfigurations = ImmutableUtils.unmodifiableSet(this.scriptConfigurations);
		this.scriptModellingExclusions = ImmutableUtils.unmodifiableSet(this.scriptModellingExclusions);
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
	public boolean isRequireTaskIDEConfiguration() {
		return requireTaskIDEConfiguration;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildDirectory == null) ? 0 : buildDirectory.hashCode());
		result = prime * result + ((connections == null) ? 0 : connections.hashCode());
		result = prime * result
				+ ((executionDaemonConnectionName == null) ? 0 : executionDaemonConnectionName.hashCode());
		result = prime * result + ((mirrorDirectory == null) ? 0 : mirrorDirectory.hashCode());
		result = prime * result + ((mounts == null) ? 0 : mounts.hashCode());
		result = prime * result + ((repositories == null) ? 0 : repositories.hashCode());
		result = prime * result + (requireTaskIDEConfiguration ? 1231 : 1237);
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
		if (requireTaskIDEConfiguration != other.requireTaskIDEConfiguration)
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

	public static Builder builder(IDEProjectProperties copy) {
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
			result.repositories = ObjectUtils.cloneLinkedHashSet(repositories);
			return this;
		}

		public Builder setUserParameters(Set<? extends Map.Entry<String, String>> userParameters) {
			result.userParameters = SakerIDEPlugin.unmodifiablizeEntrySet(userParameters);
			return this;
		}

		public Builder setConnections(Set<? extends DaemonConnectionIDEProperty> connections) {
			result.connections = ObjectUtils.cloneLinkedHashSet(connections);
			return this;
		}

		public Builder setMounts(Set<? extends ProviderMountIDEProperty> mounts) {
			result.mounts = ObjectUtils.cloneLinkedHashSet(mounts);
			return this;
		}

		public Builder setScriptConfigurations(Set<? extends ScriptConfigurationIDEProperty> scriptConfigurations) {
			result.scriptConfigurations = ObjectUtils.cloneLinkedHashSet(scriptConfigurations);
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
			result.scriptModellingExclusions = scriptModellingExclusions;
			return this;
		}

		public Builder setRequireTaskIDEConfiguration(boolean requireTaskIDEConfiguration) {
			result.requireTaskIDEConfiguration = requireTaskIDEConfiguration;
			return this;
		}

		public IDEProjectProperties build() {
			SimpleIDEProjectProperties res = this.result;
			this.result = null;
			res.unmodifiablize();
			return res;
		}

		public IDEProjectProperties buildReuse() {
			SimpleIDEProjectProperties res = new SimpleIDEProjectProperties(this.result);
			res.unmodifiablize();
			return res;
		}
	}

}