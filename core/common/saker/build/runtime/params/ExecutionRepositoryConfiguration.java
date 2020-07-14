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
package saker.build.runtime.params;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Configuration class for specifying the repositories to use during build execution.
 * <p>
 * An instance of this class can be created via the provided {@linkplain #builder() builder}.
 * <p>
 * Repositories are the main extension points for a build execution and can be used to dynamically load tasks to
 * execute.
 * <p>
 * Each repository bears an unique identifier for the build, which is either user-supplied or generated. Supplying an
 * identifier makes it easier to reference them from build scripts if it is required.
 */
@RMIWrap(ExecutionRepositoryConfiguration.ConfigurationRMIWrapper.class)
public final class ExecutionRepositoryConfiguration {
	/**
	 * The default repository identifier for the Nest repository.
	 * <p>
	 * It has a value of <code>{@value #NEST_REPOSITORY_IDENTIFIER}</code>.
	 * 
	 * @see RepositoryConfig#getRepositoryIdentifier()
	 */
	public static final String NEST_REPOSITORY_IDENTIFIER = "nest";

	/**
	 * A configuration for a given repository instance.
	 * <p>
	 * A repository configuration holds information about how the repository can be loaded. The location for its
	 * classpath, and the service enumerator to instantiate the repository.
	 * <p>
	 * The configuration also holds the user-supplied identifier to reference the repository by during build execution.
	 */
	@RMIWrap(RepoConfigRMIWrapper.class)
	public static final class RepositoryConfig {
		protected ClassPathLocation classPathLocation;
		protected ClassPathServiceEnumerator<? extends SakerRepositoryFactory> repositoryFactoryEnumerator;

		//do not include the repository identifier in the hashcode, as it can be still modified when the configuration is constructed
		//    modifying this will result in change of the hashcode, and will result in incorrect operation of the hashset containing this
		protected String repositoryIdentifier;

		protected RepositoryConfig() {
		}

		/**
		 * Creates a new repository configuration with the specified attributes.
		 * 
		 * @param classPathLocation
		 *            The classpath of the repository. May not be <code>null</code>.
		 * @param repositoryidentifier
		 *            The identifier to use during build execution. May be <code>null</code>.
		 * @param repositoryServiceEnumerator
		 *            The enumerator to look up the repository instance.
		 * @throws NullPointerException
		 *             If the classpath location or service enumerator is <code>null</code>.
		 */
		public RepositoryConfig(ClassPathLocation classPathLocation, String repositoryidentifier,
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> repositoryServiceEnumerator)
				throws NullPointerException {
			Objects.requireNonNull(classPathLocation, "classpath location");
			Objects.requireNonNull(repositoryServiceEnumerator, "service enumerator");

			this.classPathLocation = classPathLocation;
			this.repositoryIdentifier = repositoryidentifier;
			this.repositoryFactoryEnumerator = repositoryServiceEnumerator;
		}

		/**
		 * Creates a new repository configuration with the specified attributes.
		 * 
		 * @param classPathLocation
		 *            The classpath of the repository. May not be <code>null</code>.
		 * @param repositoryClassFinder
		 *            The enumerator to look up the repository instance.
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 */
		public RepositoryConfig(ClassPathLocation classPathLocation,
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> repositoryClassFinder)
				throws NullPointerException {
			this(classPathLocation, null, repositoryClassFinder);
		}

		/**
		 * Returns the {@link RepositoryConfig} for the default
		 * <a class="javadoc-external-link" href="https://saker.build/saker.nest/index.html">Nest repository</a>.
		 * <p>
		 * This default repository configuration is recommended to be used by the build system users. The repository
		 * configuration has the identifier of <code>{@value #NEST_REPOSITORY_IDENTIFIER}</code>.
		 * 
		 * @return The default repository configuration.
		 */
		public static RepositoryConfig getDefault() {
			return new RepositoryConfig(NestRepositoryClassPathLocation.getInstance(), NEST_REPOSITORY_IDENTIFIER,
					NestRepositoryFactoryClassPathServiceEnumerator.getInstance());
		}

		/**
		 * Gets the classpath location of the repository.
		 * 
		 * @return The classpath location.
		 */
		public ClassPathLocation getClassPathLocation() {
			return classPathLocation;
		}

		/**
		 * Gets the service enumerator which looks up the repository.
		 * 
		 * @return The repository service enumerator.
		 */
		public ClassPathServiceEnumerator<? extends SakerRepositoryFactory> getRepositoryFactoryEnumerator() {
			return repositoryFactoryEnumerator;
		}

		/**
		 * Gets the repository identifier to use during build execution.
		 * <p>
		 * If the user doesn't provide an identifier, an unique identifier will be generated when the
		 * {@link ExecutionRepositoryConfiguration} is constructed. (I.e. when {@link Builder#build()} is called.)
		 * 
		 * @return The repository identifier or <code>null</code> if it has not yet been determined.
		 */
		public String getRepositoryIdentifier() {
			return repositoryIdentifier;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classPathLocation == null) ? 0 : classPathLocation.hashCode());
			result = prime * result
					+ ((repositoryFactoryEnumerator == null) ? 0 : repositoryFactoryEnumerator.hashCode());
			//no repositoryidentifier
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
			RepositoryConfig other = (RepositoryConfig) obj;
			if (classPathLocation == null) {
				if (other.classPathLocation != null)
					return false;
			} else if (!classPathLocation.equals(other.classPathLocation))
				return false;
			if (repositoryFactoryEnumerator == null) {
				if (other.repositoryFactoryEnumerator != null)
					return false;
			} else if (!repositoryFactoryEnumerator.equals(other.repositoryFactoryEnumerator))
				return false;
			if (repositoryIdentifier == null) {
				if (other.repositoryIdentifier != null)
					return false;
			} else if (!repositoryIdentifier.equals(other.repositoryIdentifier))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "["
					+ (classPathLocation != null ? "classPathLocation=" + classPathLocation + ", " : "")
					+ (repositoryIdentifier != null ? "repositoryIdentifier=" + repositoryIdentifier + ", " : "")
					+ (repositoryFactoryEnumerator != null
							? "repositoryFactoryEnumerator=" + repositoryFactoryEnumerator
							: "")
					+ "]";
		}
	}

	/**
	 * Builder class for {@link ExecutionRepositoryConfiguration}.
	 * <p>
	 * The builder is single use, it cannot be reused after callig {@link #build()}.
	 */
	public final static class Builder {
		private Set<RepositoryConfig> repositories = new LinkedHashSet<>();

		protected Builder() {
		}

		/**
		 * Adds a repository for the configuration with the given classpath location and enumerator.
		 * 
		 * @param location
		 *            The classpath to load the repository from.
		 * @param enumerator
		 *            The service enumerator to use when instantiating the repository.
		 * @return <code>this</code>
		 * @throws IllegalStateException
		 *             If the builder was already consumed.
		 */
		public Builder add(ClassPathLocation location,
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) throws IllegalStateException {
			return add(new RepositoryConfig(location, null, enumerator));
		}

		/**
		 * Adds a repository for the configuration with the given attributes.
		 * 
		 * @param location
		 *            The classpath to load the repository from.
		 * @param enumerator
		 *            The service enumerator to use when instantiating the repository.
		 * @param repositoryidentifier
		 *            The identifier for the repository to use during execution. May be <code>null</code>.
		 * @return <code>this</code>
		 * @throws IllegalStateException
		 *             If the builder was already consumed.
		 */
		public Builder add(ClassPathLocation location,
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator, String repositoryidentifier)
				throws IllegalStateException {
			return add(new RepositoryConfig(location, repositoryidentifier, enumerator));
		}

		/**
		 * Add a repository configuration to the builder.
		 * 
		 * @param repoconfig
		 *            The repository configuration.
		 * @return <code>this</code>
		 * @throws IllegalStateException
		 *             If the builder was already consumed.
		 */
		public Builder add(RepositoryConfig repoconfig) throws IllegalStateException {
			getRepositoriesStateCheck().add(repoconfig);
			return this;
		}

		/**
		 * Builds the {@link ExecutionRepositoryConfiguration}.
		 * <p>
		 * The builder is no longer useable if this call succeeds.
		 * 
		 * @return The constructed repository configuration.
		 * @throws InvalidBuildConfigurationException
		 *             If multiple repositories were defined for the same identifier.
		 * @throws IllegalStateException
		 *             If the builder was already consumed.
		 */
		public ExecutionRepositoryConfiguration build()
				throws InvalidBuildConfigurationException, IllegalStateException {
			Set<RepositoryConfig> repos = getRepositoriesStateCheck();
			Set<String> repoids = new TreeSet<>();
			ExecutionRepositoryConfiguration result = new ExecutionRepositoryConfiguration(
					ImmutableUtils.unmodifiableSet(repos));
			for (RepositoryConfig rc : result.repositories) {
				if (rc.repositoryIdentifier != null) {
					if (!repoids.add(rc.repositoryIdentifier)) {
						throw new InvalidBuildConfigurationException(
								"Duplicate repository identifiers: " + rc.repositoryIdentifier);
					}
				}
			}
			for (RepositoryConfig rc : result.repositories) {
				if (rc.repositoryIdentifier == null) {
					String cplid = rc.classPathLocation.getIdentifier();
					String id = "repo_" + cplid;
					int idx = 1;
					while (true) {
						if (repoids.add(id)) {
							rc.repositoryIdentifier = id;
							break;
						}
						id = "repo_" + cplid + "$" + (idx++);
					}
				}
			}
			this.repositories = null;
			return result;
		}

		private Set<RepositoryConfig> getRepositoriesStateCheck() {
			Set<RepositoryConfig> repos = repositories;
			if (repos == null) {
				throw new IllegalStateException("Builder already used.");
			}
			return repos;
		}
	}

	private Set<RepositoryConfig> repositories;

	/**
	 * For RMI serialization.
	 */
	private ExecutionRepositoryConfiguration() {
	}

	private ExecutionRepositoryConfiguration(Set<RepositoryConfig> locationIdRepositories) {
		this.repositories = locationIdRepositories;
	}

	private static final ExecutionRepositoryConfiguration DEFAULT_REPOSITORY_CONFIG;
	static {
		Builder builder = ExecutionRepositoryConfiguration.builder();
		builder.add(RepositoryConfig.getDefault());
		DEFAULT_REPOSITORY_CONFIG = builder.build();
	}

	/**
	 * Gets the default repository configuration for the build system.
	 * <p>
	 * The default repository configuration contains the
	 * <a class="javadoc-external-link" href="https://saker.build/saker.nest/index.html">Nest repository</a> which is
	 * considered to be the default repository included with the build system. It has the identifier of
	 * <code>{@value #NEST_REPOSITORY_IDENTIFIER}</code>.
	 * 
	 * @return The default configuration.
	 * @see RepositoryConfig#getDefault()
	 */
	public static ExecutionRepositoryConfiguration getDefault() {
		return DEFAULT_REPOSITORY_CONFIG;
	}

	/**
	 * Gets the empty configuration that is configured to use no repositories.
	 * 
	 * @return The empty configuration.
	 */
	public static ExecutionRepositoryConfiguration empty() {
		return new ExecutionRepositoryConfiguration(Collections.emptySet());
	}

	/**
	 * Creates a new empty builder that holds no repositories.
	 * 
	 * @return The created builder.
	 */
	public static ExecutionRepositoryConfiguration.Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a new builder and initializes it with the specified configuration.
	 * 
	 * @param config
	 *            The configuration.
	 * @return The created builder.
	 */
	public static ExecutionRepositoryConfiguration.Builder builder(ExecutionRepositoryConfiguration config) {
		Builder result = new Builder();
		for (RepositoryConfig repoconfig : config.repositories) {
			result.add(repoconfig);
		}
		return result;
	}

	/**
	 * Gets the configured repositories to use during build execution.
	 * 
	 * @return The repository configurations.
	 */
	public Collection<? extends RepositoryConfig> getRepositories() {
		return repositories;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((repositories == null) ? 0 : repositories.hashCode());
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
		ExecutionRepositoryConfiguration other = (ExecutionRepositoryConfiguration) obj;
		if (repositories == null) {
			if (other.repositories != null)
				return false;
		} else if (!repositories.equals(other.repositories))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (repositories != null ? "repositories=" + repositories : "") + "]";
	}

	protected static final class ConfigurationRMIWrapper implements RMIWrapper {
		private ExecutionRepositoryConfiguration configuration;

		public ConfigurationRMIWrapper() {
		}

		public ConfigurationRMIWrapper(ExecutionRepositoryConfiguration pathConfiguration) {
			this.configuration = pathConfiguration;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, configuration.repositories);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			configuration = new ExecutionRepositoryConfiguration();
			configuration.repositories = SerialUtils.readExternalImmutableLinkedHashSet(in);
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return configuration;
		}

	}

	protected static final class RepoConfigRMIWrapper implements RMIWrapper {
		private RepositoryConfig configuration;

		public RepoConfigRMIWrapper() {
		}

		public RepoConfigRMIWrapper(RepositoryConfig configuration) {
			this.configuration = configuration;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeObject(configuration.classPathLocation);
			out.writeObject(configuration.repositoryFactoryEnumerator);
			out.writeUTF(configuration.repositoryIdentifier);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			ClassPathLocation location = (ClassPathLocation) in.readObject();
			@SuppressWarnings("unchecked")
			ClassPathServiceEnumerator<? extends SakerRepositoryFactory> factoryenumerator = (ClassPathServiceEnumerator<? extends SakerRepositoryFactory>) in
					.readObject();
			String repoid = in.readUTF();
			configuration = new RepositoryConfig();
			configuration.classPathLocation = location;
			configuration.repositoryIdentifier = repoid;
			configuration.repositoryFactoryEnumerator = factoryenumerator;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return configuration;
		}

	}
}
