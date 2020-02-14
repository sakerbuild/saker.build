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
package testing.saker.build.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public final class EnvironmentTestCaseConfiguration implements Cloneable {
	private boolean useProject;
	private boolean projectFileWatchingEnabled = true;
	private Set<String> clusterNames = Collections.emptySet();
	private String environmentStorageDirectory = "common";
	private Boolean useCommonEnvironment = null;
	private Map<String, String> environmentUserParameters = null;
	private ProviderHolderPathKey buildTraceOutputPathKey = null;

	private EnvironmentTestCaseConfiguration() {
	}

	public Set<String> getClusterNames() {
		return clusterNames;
	}

	public boolean isUseProject() {
		return useProject;
	}

	public Boolean isUseCommonEnvironment() {
		return useCommonEnvironment;
	}

	public String getEnvironmentStorageDirectory() {
		return environmentStorageDirectory;
	}

	public Map<String, String> getEnvironmentUserParameters() {
		return environmentUserParameters;
	}

	public boolean isProjectFileWatchingEnabled() {
		return projectFileWatchingEnabled;
	}

	public ProviderHolderPathKey getBuildTraceOutputPathKey() {
		return buildTraceOutputPathKey;
	}

	@Override
	protected EnvironmentTestCaseConfiguration clone() {
		try {
			return (EnvironmentTestCaseConfiguration) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(EnvironmentTestCaseConfiguration copy) {
		return new Builder(copy);
	}

	public static MultiBuilder builder(Collection<EnvironmentTestCaseConfiguration> configs) {
		return new MultiBuilder(configs);
	}

	public static final class Builder {
		private final EnvironmentTestCaseConfiguration result;

		Builder() {
			this.result = new EnvironmentTestCaseConfiguration();
		}

		Builder(EnvironmentTestCaseConfiguration copy) {
			this.result = copy.clone();
		}

		public Builder setClusterNames(Set<String> useClusterNames) {
			result.clusterNames = ObjectUtils.isNullOrEmpty(useClusterNames) ? Collections.emptySet()
					: ImmutableUtils.unmodifiableNavigableSet(new TreeSet<>(useClusterNames));
			return this;
		}

		public Builder addClusterName(String name) {
			result.clusterNames = ObjectUtils.isNullOrEmpty(result.clusterNames)
					? ImmutableUtils.singletonNavigableSet(name)
					: ImmutableUtils.unmodifiableNavigableSet(
							ObjectUtils.addAll(ObjectUtils.newTreeSet(result.clusterNames), name));
			return this;
		}

		public Builder setUseProject(boolean useProject) {
			result.useProject = useProject;
			return this;
		}

		public Builder setEnvironmentStorageDirectory(String environmentstoragedirectory) {
			result.environmentStorageDirectory = environmentstoragedirectory;
			return this;
		}

		public Builder setUseCommonEnvironment(boolean useCommonEnvironment) {
			result.useCommonEnvironment = useCommonEnvironment;
			return this;
		}

		public Builder setProjectFileWatchingEnabled(boolean projectfilewatchingenabled) {
			result.projectFileWatchingEnabled = projectfilewatchingenabled;
			return this;
		}

		public Builder setEnvironmentUserParameters(Map<String, String> environmentUserParameters) {
			result.environmentUserParameters = ImmutableUtils.makeImmutableNavigableMap(environmentUserParameters);
			return this;
		}

		public Builder setBuildTraceOutputPathKey(ProviderHolderPathKey buildTraceOutputPathKey) {
			result.buildTraceOutputPathKey = buildTraceOutputPathKey;
			return this;
		}

		public EnvironmentTestCaseConfiguration build() {
			return result.clone();
		}
	}

	public static final class MultiBuilder {
		private Collection<Builder> builders;

		MultiBuilder(Collection<EnvironmentTestCaseConfiguration> configs) {
			this.builders = new ArrayList<>(configs.size());
			for (EnvironmentTestCaseConfiguration config : configs) {
				this.builders.add(builder(config));
			}
		}

		public MultiBuilder setClusterNames(Set<String> clusterNames) {
			for (Builder builder : builders) {
				builder.setClusterNames(clusterNames);
			}
			return this;
		}

		public MultiBuilder addClusterName(String clusterNames) {
			for (Builder builder : builders) {
				builder.addClusterName(clusterNames);
			}
			return this;
		}

		public MultiBuilder setUseProject(boolean useProject) {
			for (Builder builder : builders) {
				builder.setUseProject(useProject);
			}
			return this;
		}

		public MultiBuilder setEnvironmentStorageDirectory(String environmentstoragedirectory) {
			for (Builder builder : builders) {
				builder.setEnvironmentStorageDirectory(environmentstoragedirectory);
			}
			return this;
		}

		public MultiBuilder setUseCommonEnvironment(boolean useCommonEnvironment) {
			for (Builder builder : builders) {
				builder.setUseCommonEnvironment(useCommonEnvironment);
			}
			return this;
		}

		public MultiBuilder setProjectFileWatchingEnabled(boolean projectfilewatchingenabled) {
			for (Builder builder : builders) {
				builder.setProjectFileWatchingEnabled(projectfilewatchingenabled);
			}
			return this;
		}

		public MultiBuilder setEnvironmentUserParameters(Map<String, String> environmentUserParameters) {
			for (Builder builder : builders) {
				builder.setEnvironmentUserParameters(environmentUserParameters);
			}
			return this;
		}

		public MultiBuilder setBuildTraceOutputPathKey(ProviderHolderPathKey buildTraceOutputPathKey) {
			for (Builder builder : builders) {
				builder.setBuildTraceOutputPathKey(buildTraceOutputPathKey);
			}
			return this;
		}

		public Set<EnvironmentTestCaseConfiguration> build() {
			Set<EnvironmentTestCaseConfiguration> result = new LinkedHashSet<>();
			for (Builder builder : builders) {
				result.add(builder.build());
			}
			return result;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildTraceOutputPathKey == null) ? 0 : buildTraceOutputPathKey.hashCode());
		result = prime * result + ((clusterNames == null) ? 0 : clusterNames.hashCode());
		result = prime * result + ((environmentStorageDirectory == null) ? 0 : environmentStorageDirectory.hashCode());
		result = prime * result + ((environmentUserParameters == null) ? 0 : environmentUserParameters.hashCode());
		result = prime * result + (projectFileWatchingEnabled ? 1231 : 1237);
		result = prime * result + ((useCommonEnvironment == null) ? 0 : useCommonEnvironment.hashCode());
		result = prime * result + (useProject ? 1231 : 1237);
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
		EnvironmentTestCaseConfiguration other = (EnvironmentTestCaseConfiguration) obj;
		if (buildTraceOutputPathKey == null) {
			if (other.buildTraceOutputPathKey != null)
				return false;
		} else if (!buildTraceOutputPathKey.equals(other.buildTraceOutputPathKey))
			return false;
		if (clusterNames == null) {
			if (other.clusterNames != null)
				return false;
		} else if (!clusterNames.equals(other.clusterNames))
			return false;
		if (environmentStorageDirectory == null) {
			if (other.environmentStorageDirectory != null)
				return false;
		} else if (!environmentStorageDirectory.equals(other.environmentStorageDirectory))
			return false;
		if (environmentUserParameters == null) {
			if (other.environmentUserParameters != null)
				return false;
		} else if (!environmentUserParameters.equals(other.environmentUserParameters))
			return false;
		if (projectFileWatchingEnabled != other.projectFileWatchingEnabled)
			return false;
		if (useCommonEnvironment == null) {
			if (other.useCommonEnvironment != null)
				return false;
		} else if (!useCommonEnvironment.equals(other.useCommonEnvironment))
			return false;
		if (useProject != other.useProject)
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[useProject=");
		sb.append(useProject);
		if (useProject) {
			sb.append(", projectFileWatchingEnabled=");
			sb.append(projectFileWatchingEnabled);
		}
		if (!ObjectUtils.isNullOrEmpty(clusterNames)) {
			sb.append(", ");
			sb.append((clusterNames != null ? "clusterNames=" + clusterNames + ", " : ""));
		}
		sb.append((environmentStorageDirectory != null ? ", environmentStorageDirectory=" + environmentStorageDirectory
				: ""));
		if (environmentUserParameters != null) {
			sb.append(", environmentUserParameters=");
			sb.append(environmentUserParameters);
		}
		if (buildTraceOutputPathKey != null) {
			sb.append(", buildTraceOutputPathKey=");
			sb.append(buildTraceOutputPathKey);
		}
		sb.append("]");
		return sb.toString();
	}

}
