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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ScriptAccessorClassPathCacheKey;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.SimpleScriptParsingOptions;
import saker.build.scripting.TargetConfigurationReader;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.util.cache.CacheKey;

/**
 * Configuration class for specifying the script language, options, and classpath information for a build execution.
 * <p>
 * An instance of this class can be created via the provided {@linkplain #builder() builder}.
 * <p>
 * Clients can configure the how the script files should be handled by the build system based on its file path. The
 * configuration uses {@linkplain WildcardPath wildcard paths} to determine the correct scripting options and script
 * language to use for a file.
 * <p>
 * If the specified wildcard paths are ambiguous, then the first added configuration will be used for a given path.
 * (Ambiguity can happen if multiple wildcard paths can match a specific path.)
 * <p>
 * Users can specify an arbitrary string key-value map to pass to the scripting language parser, and the language to use
 * for the given file.
 */
@RMIWrap(ExecutionScriptConfiguration.ConfigurationRMIWrapper.class)
public final class ExecutionScriptConfiguration {

	/**
	 * An options configuration specifying the parsing options and the location of the scripting language parser.
	 * <p>
	 * This class is used in conjunction with the wildcard paths specified for a path. The class holds the key-value map
	 * to pass to the language parser, and the configuration for creating the parser itself.
	 */
	@RMIWrap(ScriptConfigRMIWrapper.class)
	public static final class ScriptOptionsConfig {
		protected Map<String, String> options;
		protected ScriptProviderLocation providerLocation;

		protected ScriptOptionsConfig() {
		}

		/**
		 * Creates a new script options configuration instance.
		 * 
		 * @param options
		 *            The options to pass to the scripting language parser. May be <code>null</code> or empty.
		 * @param providerLocation
		 *            The location of the scripting language provider.
		 * @throws NullPointerException
		 *             If the provider location is <code>null</code>.
		 * @see ScriptProviderLocation#getBuiltin()
		 */
		public ScriptOptionsConfig(Map<String, String> options, ScriptProviderLocation providerLocation)
				throws NullPointerException {
			Objects.requireNonNull(providerLocation, "provider location");
			this.options = ObjectUtils.isNullOrEmpty(options) ? Collections.emptyMap()
					: ImmutableUtils.makeImmutableLinkedHashMap(options);
			this.providerLocation = providerLocation;
		}

		/**
		 * Gets the user provided parsing options for this configuration.
		 * 
		 * @return The parsing options.
		 * @see TargetConfigurationReader#readConfiguration(ScriptParsingOptions, ByteSource)
		 */
		public Map<String, String> getOptions() {
			return options;
		}

		/**
		 * Gets the location of the script provider.
		 * 
		 * @return The script provider location.
		 */
		public ScriptProviderLocation getProviderLocation() {
			return providerLocation;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((options == null) ? 0 : options.hashCode());
			result = prime * result + ((providerLocation == null) ? 0 : providerLocation.hashCode());
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
			ScriptOptionsConfig other = (ScriptOptionsConfig) obj;
			if (options == null) {
				if (other.options != null)
					return false;
			} else if (!options.equals(other.options))
				return false;
			if (providerLocation == null) {
				if (other.providerLocation != null)
					return false;
			} else if (!providerLocation.equals(other.providerLocation))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (options != null ? "options=" + options + ", " : "")
					+ (providerLocation != null ? "providerLocation=" + providerLocation : "") + "]";
		}

	}

	/**
	 * Class holding information about how a {@linkplain ScriptAccessProvider scripting language provider} should be
	 * retrieved.
	 * <p>
	 * The class holds a {@link ClassPathLocation} and a {@link ClassPathServiceEnumerator} instance which together
	 * specify how a script language can be loaded.
	 */
	@RMIWrap(ProviderLocationRMIWrapper.class)
	public static final class ScriptProviderLocation {
		private static final ScriptProviderLocation BUILTIN_LOCATION = new ScriptProviderLocation();
		static {
			BUILTIN_LOCATION.scriptProviderEnumerator = BuiltinScriptAccessorServiceEnumerator.getInstance();
		}

		protected ClassPathLocation classPathLocation;
		protected ClassPathServiceEnumerator<? extends ScriptAccessProvider> scriptProviderEnumerator;

		protected ScriptProviderLocation() {
		}

		/**
		 * Creates a new script provider location.
		 * 
		 * @param classPathLocation
		 *            The classpath location to load. May be <code>null</code> to use the classloader of the build
		 *            system.
		 * @param accessProviderEnumerator
		 *            The service enumerator for the requested scripting language.
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 */
		public ScriptProviderLocation(ClassPathLocation classPathLocation,
				ClassPathServiceEnumerator<? extends ScriptAccessProvider> accessProviderEnumerator)
				throws NullPointerException {
			Objects.requireNonNull(classPathLocation, "classpath location");
			Objects.requireNonNull(accessProviderEnumerator, "service enumerator");
			this.classPathLocation = classPathLocation;
			this.scriptProviderEnumerator = accessProviderEnumerator;
		}

		/**
		 * Gets the script provider location for the built-in language.
		 * 
		 * @return The script provider location.
		 */
		public static ScriptProviderLocation getBuiltin() {
			return BUILTIN_LOCATION;
		}

		/**
		 * Gets the classpath location to load for retrieving the scripting language provider.
		 * <p>
		 * The classpath location may be <code>null</code> if the built-in script provider is used.
		 * 
		 * @return The classpath location or <code>null</code> if the built-in script provider is used.
		 * @see BuiltinScriptAccessorServiceEnumerator
		 */
		public ClassPathLocation getClassPathLocation() {
			return classPathLocation;
		}

		/**
		 * Gets the service enumerator to look up the scripting language provider.
		 * 
		 * @return The service enumerator.
		 */
		public ClassPathServiceEnumerator<? extends ScriptAccessProvider> getScriptProviderEnumerator() {
			return scriptProviderEnumerator;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((scriptProviderEnumerator == null) ? 0 : scriptProviderEnumerator.hashCode());
			result = prime * result + ((classPathLocation == null) ? 0 : classPathLocation.hashCode());
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
			ScriptProviderLocation other = (ScriptProviderLocation) obj;
			if (scriptProviderEnumerator == null) {
				if (other.scriptProviderEnumerator != null)
					return false;
			} else if (!scriptProviderEnumerator.equals(other.scriptProviderEnumerator))
				return false;
			if (classPathLocation == null) {
				if (other.classPathLocation != null)
					return false;
			} else if (!classPathLocation.equals(other.classPathLocation))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "["
					+ (classPathLocation != null ? "classPathLocation=" + classPathLocation + ", " : "")
					+ (scriptProviderEnumerator != null ? "scriptProviderEnumerator=" + scriptProviderEnumerator : "")
					+ "]";
		}

	}

	/**
	 * Builder class for {@link ExecutionScriptConfiguration}.
	 * <p>
	 * The builder is single use, it cannot be reused after callig {@link #build()}.
	 */
	public static final class Builder {
		private Set<ScriptProviderLocation> providerLocations = new HashSet<>();
		private Map<WildcardPath, ScriptOptionsConfig> configurations = new LinkedHashMap<>();

		protected Builder() {
		}

		/**
		 * Adds a configuration for the specified wildcard path to the builder.
		 * <p>
		 * This method doesn't overwrite the configuration if it was already defined for the specified wildcard path,
		 * but will thrown an {@link IllegalArgumentException}.
		 * <p>
		 * During runtime, if a path matches multiple wildcards, the first one that matches it will be used.
		 * 
		 * @param appliedpaths
		 *            The paths to apply the configurations for.
		 * @param config
		 *            The configuration.
		 * @return <code>this</code>
		 * @throws IllegalArgumentException
		 *             If a configuration were already defined for the given wildcard path.
		 */
		public Builder addConfig(WildcardPath appliedpaths, ScriptOptionsConfig config)
				throws IllegalArgumentException {
			ScriptOptionsConfig prev = configurations.putIfAbsent(appliedpaths, config);
			if (prev != null) {
				throw new IllegalArgumentException(
						"Script options were already defined for path: " + appliedpaths + " (" + prev + ")");
			}
			if (config.providerLocation != null) {
				providerLocations.add(config.providerLocation);
			}
			return this;
		}

		/**
		 * Builds the {@link ExecutionScriptConfiguration}.
		 * <p>
		 * The builder is no longer useable if this call succeeds.
		 * 
		 * @return The constructed script configuration.
		 */
		public ExecutionScriptConfiguration build() {
			Map<WildcardPath, ScriptOptionsConfig> configs = configurations;
			if (configs == null) {
				throw new IllegalStateException("Builder already used.");
			}
			ExecutionScriptConfiguration result = new ExecutionScriptConfiguration();
			result.providerLocations = ImmutableUtils.unmodifiableSet(this.providerLocations);
			result.configurations = ImmutableUtils.unmodifiableMap(configs);
			this.configurations = null;
			return result;
		}
	}

	private static final ExecutionScriptConfiguration DEFAULT_CONFIG = new ExecutionScriptConfiguration();
	static {
		DEFAULT_CONFIG.providerLocations = ImmutableUtils.singletonSet(ScriptProviderLocation.getBuiltin());
		DEFAULT_CONFIG.configurations = ImmutableUtils.singletonMap(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(Collections.emptyMap(), ScriptProviderLocation.getBuiltin()));
	}

	private transient Set<ScriptProviderLocation> providerLocations;
	/**
	 * Unmodifiable map of the script configurations mapped by the applying file paths to config objects.
	 */
	private Map<WildcardPath, ScriptOptionsConfig> configurations;

	/**
	 * For RMI serialization.
	 */
	private ExecutionScriptConfiguration() {
	}

	/**
	 * Creates a new empty builder.
	 * <p>
	 * The builder doesn't contain any configuration, not even for the built-in scripting language.
	 * 
	 * @return The created builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a new builder and initializes it with the specified configuration.
	 * 
	 * @param config
	 *            The configuration.
	 * @return The created builder.
	 */
	public static Builder builder(ExecutionScriptConfiguration config) {
		Builder builder = new Builder();
		for (Entry<WildcardPath, ScriptOptionsConfig> entry : config.configurations.entrySet()) {
			builder.addConfig(entry.getKey(), entry.getValue());
		}
		return builder;
	}

	/**
	 * Gets an empty script configuration.
	 * <p>
	 * The resulting configuration is empty, no scripting languages are available in it. Any build execution started
	 * with it most likely won't be able to parse build scripts.
	 * 
	 * @return An empty configuration.
	 */
	public static ExecutionScriptConfiguration empty() {
		ExecutionScriptConfiguration result = new ExecutionScriptConfiguration();
		result.providerLocations = Collections.emptySet();
		result.configurations = Collections.emptyMap();
		return result;
	}

	/**
	 * Gets the default script configuration which uses the built-in scripting language.
	 * <p>
	 * The default configuration uses the built-in scripting langauge for every script file that ends with the extension
	 * <code>".build"</code> (case sensitive).
	 * <p>
	 * The returned configuration is a singleton instance.
	 * 
	 * @return The default script configuration.
	 * @see ScriptProviderLocation#getBuiltin()
	 */
	public static ExecutionScriptConfiguration getDefault() {
		return DEFAULT_CONFIG;
	}

	/**
	 * Gets the script provider locations which are used in this script configuration.
	 * 
	 * @return The script provider locations.
	 */
	public Set<? extends ScriptProviderLocation> getScriptProviderLocations() {
		return providerLocations;
	}

	/**
	 * Gets the script configurations mapped to their wildcard paths.
	 * 
	 * @return The configurations.
	 */
	public Map<WildcardPath, ? extends ScriptOptionsConfig> getConfigurations() {
		return configurations;
	}

	/**
	 * Gets the script options configuration for the given script path.
	 * 
	 * @param scriptpath
	 *            The script path.
	 * @return The script options configuration or <code>null</code> if no configuration was defined for the specified
	 *             path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 */
	public ScriptOptionsConfig getScriptOptionsConfig(SakerPath scriptpath) throws InvalidPathFormatException {
		for (Entry<WildcardPath, ScriptOptionsConfig> entry : this.configurations.entrySet()) {
			WildcardPath wc = entry.getKey();
			if (wc.includes(scriptpath)) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Creates a script parsing options for a script path if it was configured.
	 * 
	 * @param scriptpath
	 *            The script path.
	 * @return The script parsing options to use to parse the script or <code>null</code> if no configuration is set for
	 *             the path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 */
	public ScriptParsingOptions getScriptParsingOptions(SakerPath scriptpath) throws InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(scriptpath);
		ScriptOptionsConfig optionsconfig = getScriptOptionsConfig(scriptpath);
		if (optionsconfig == null) {
			return null;
		}
		return new SimpleScriptParsingOptions(scriptpath, optionsconfig.getOptions());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configurations == null) ? 0 : configurations.hashCode());
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
		ExecutionScriptConfiguration other = (ExecutionScriptConfiguration) obj;
		if (configurations == null) {
			if (other.configurations != null)
				return false;
		} else if (!configurations.equals(other.configurations))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (configurations != null ? "configurations=" + configurations : "")
				+ "]";
	}

	/**
	 * Retrieves the script language provider specified by the given provider location argument and uses the specified
	 * environment to manage the class loading.
	 * <p>
	 * This method will use the {@linkplain SakerEnvironment#getCachedData(CacheKey) caching facility} of the given
	 * environment to load the classes specified by the location argument. The {@linkplain ClassPathLocation class path
	 * location} will be loaded, and its lifecycle will be automatically managed by the argument environment. The
	 * appropriate {@link ScriptAccessProvider} instance will be returned.
	 * 
	 * @param environment
	 *            The environment to load the classes with.
	 * @param providerlocation
	 *            The location of the scripting language provider.
	 * @return The loaded scripting language provider.
	 * @throws Exception
	 *             If for any reason the loading of the language failed. (Usually I/O errors or misconfigurations.)
	 */
	public static ScriptAccessProvider getScriptAccessorProvider(SakerEnvironment environment,
			ScriptProviderLocation providerlocation) throws Exception {
		return environment
				.getCachedData(new ScriptAccessorClassPathCacheKey(providerlocation, environment.getClassPathManager()))
				.getScriptAccessor();
	}

	protected static final class ConfigurationRMIWrapper implements RMIWrapper {
		private ExecutionScriptConfiguration configuration;

		public ConfigurationRMIWrapper() {
		}

		public ConfigurationRMIWrapper(ExecutionScriptConfiguration configuration) {
			this.configuration = configuration;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, configuration.providerLocations);
			SerialUtils.writeExternalMap(out, configuration.configurations);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			configuration = new ExecutionScriptConfiguration();
			configuration.providerLocations = SerialUtils.readExternalImmutableHashSet(in);
			configuration.configurations = SerialUtils.readExternalImmutableLinkedHashMap(in);
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

	protected static final class ProviderLocationRMIWrapper implements RMIWrapper {
		private ScriptProviderLocation location;

		public ProviderLocationRMIWrapper() {
		}

		public ProviderLocationRMIWrapper(ScriptProviderLocation location) {
			this.location = location;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeObject(location.classPathLocation);
			out.writeObject(location.scriptProviderEnumerator);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			ClassPathLocation cplocation = (ClassPathLocation) in.readObject();
			@SuppressWarnings("unchecked")
			ClassPathServiceEnumerator<? extends ScriptAccessProvider> enumerator = (ClassPathServiceEnumerator<? extends ScriptAccessProvider>) in
					.readObject();
			location = new ScriptProviderLocation();
			location.classPathLocation = cplocation;
			location.scriptProviderEnumerator = enumerator;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return location;
		}
	}

	protected static final class ScriptConfigRMIWrapper implements RMIWrapper {
		private ScriptOptionsConfig config;

		public ScriptConfigRMIWrapper() {
		}

		public ScriptConfigRMIWrapper(ScriptOptionsConfig config) {
			this.config = config;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			SerialUtils.writeExternalMap(out, config.options);
			out.writeObject(config.providerLocation);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			Map<String, String> options = SerialUtils.readExternalImmutableLinkedHashMap(in);
			ScriptProviderLocation providerloc = (ScriptProviderLocation) in.readObject();
			config = new ScriptOptionsConfig();
			config.options = options;
			config.providerLocation = providerloc;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return config;
		}

	}
}
