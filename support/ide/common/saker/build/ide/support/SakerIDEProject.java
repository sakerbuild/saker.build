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
package saker.build.ide.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import saker.build.daemon.BuildExecutionInvoker;
import saker.build.daemon.DaemonEnvironment;
import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.DirectoryMountFileProvider;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.ide.configuration.SimpleIDEConfiguration;
import saker.build.ide.support.SakerIDEPlugin.PluginResourceListener;
import saker.build.ide.support.configuration.ProjectIDEConfigurationCollection;
import saker.build.ide.support.persist.StructuredArrayObjectInput;
import saker.build.ide.support.persist.StructuredArrayObjectOutput;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.MountPathIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.PropertiesValidationException;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.ide.support.util.EmptySakerFileProvider;
import saker.build.ide.support.util.ThreadedResourceLoader;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.NamedCheckingClassPathServiceEnumerator;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.environment.BuildTaskExecutionResultImpl;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation.ConnectionInformation;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.params.BuiltinScriptAccessorServiceEnumerator;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.params.NestRepositoryClassPathLocation;
import saker.build.runtime.params.NestRepositoryFactoryClassPathServiceEnumerator;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.runtime.project.SakerExecutionCache;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.SimpleScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.NetworkUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;

public final class SakerIDEProject {
	private static final SakerPath SCRIPTING_PSEUDO_PATH = SakerPath.valueOf("scriptingpseudoroot:");
	private static final NavigableSet<String> SCRIPTING_PSEUDO_ROOTS = ImmutableUtils
			.singletonNavigableSet(SCRIPTING_PSEUDO_PATH.getRoot());
	private static final EmptySakerFileProvider SCRIPTING_PSEUDO_EMPTY_FILE_PROVIDER = new EmptySakerFileProvider(
			SCRIPTING_PSEUDO_ROOTS);

	public interface ProjectResourceListener {
		/**
		 * @param env
		 *            The script model environment that is being closed.
		 */
		public default void scriptModellingEnvironmentClosing(ScriptModellingEnvironment env) {
		}

		/**
		 * @param env
		 *            The created script model environment..
		 */
		public default void scriptModellingEnvironmentCreated(ScriptModellingEnvironment env) {
		}
	}

	public static final String MOUNT_ENDPOINT_LOCAL_FILESYSTEM = "local";
	public static final String MOUNT_ENDPOINT_EXECUTION_DAEMON = "remote";
	public static final String MOUNT_ENDPOINT_PROJECT_RELATIVE = "project";

	public static final String DEFAULT_BUILD_FILE_NAME = "saker.build";
	private static final String CONFIG_FILE_ROOT_OBJECT_NAME = "saker.build.ide.project.config";
	public static final String PROPERTIES_FILE_NAME = "." + CONFIG_FILE_ROOT_OBJECT_NAME;

	private static final String IDE_CONFIG_FILE_ROOT_OBJECT_NAME = "saker.build.ideconfigs";
	private static final String IDE_CONFIG_FILE_NAME = "." + IDE_CONFIG_FILE_ROOT_OBJECT_NAME;

	private static final String DEFAULT_WORKING_DIRECTORY_ROOT = "wd:";
	public static final String DEFAULT_BUILD_DIRECTORY_PATH = "build";
	public static final ProviderMountIDEProperty DEFAULT_MOUNT_IDE_PROPERTY = new ProviderMountIDEProperty(
			DEFAULT_WORKING_DIRECTORY_ROOT,
			MountPathIDEProperty.create(MOUNT_ENDPOINT_PROJECT_RELATIVE, SakerPath.ROOT_SLASH));
	public static final ScriptConfigurationIDEProperty DEFAULT_SCRIPT_IDE_PROPERTY = new ScriptConfigurationIDEProperty(
			"**/*.build", Collections.emptySet(), BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE,
			BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE);
	public static final RepositoryIDEProperty DEFAULT_REPOSITORY_IDE_PROPERTY = new RepositoryIDEProperty(
			new NestRepositoryClassPathLocationIDEProperty(),
			ExecutionRepositoryConfiguration.NEST_REPOSITORY_IDENTIFIER,
			new NestRepositoryFactoryServiceEnumeratorIDEProperty());

	public static final String NS_DAEMON_CONNECTION = "daemon-connection.";
	public static final String NS_PROVIDER_MOUNT = "provider-mount.";
	public static final String NS_BUILD_TRACE_OUT = "build-trace-out.";
	public static final String NS_EXECUTION_DAEMON_NAME = "execution-daemon-name.";
	public static final String NS_USER_PARAMETERS = "user-parameters.";
	public static final String NS_SCRIPT_CONFIGURATION = "script-configuration.";
	public static final String NS_REPOSITORY_CONFIGURATION = "repository-configuration.";
	public static final String NS_MIRROR_DIRECTORY = "mirror-directory.";
	public static final String NS_WORKING_DIRECTORY = "working-directory.";
	public static final String NS_BUILD_DIRECTORY = "build-directory.";
	public static final String NS_SCRIPT_MODELLING_EXCLUSION = "script-modelling-exclusion";

	public static final String C_OPTIONS = "options.";
	public static final String C_CLASSPATH = "classpath.";
	public static final String C_SERVICE = "service.";
	public static final String C_WILDCARD = "wildcard.";
	public static final String C_IDENTIFIER = "identifier.";
	public static final String C_ADDRESS = "address.";
	public static final String C_NAME = "name.";
	public static final String C_ROOT = "root.";
	public static final String C_CLIENT = "client.";
	public static final String C_PATH = "path.";
	public static final String C_JAR = "jar.";
	public static final String C_URL = "url.";
	public static final String C_HTTP_URL_JAR = "http-url-jar.";
	public static final String C_CONNECTION = "connection.";
	public static final String C_CLASS = "class.";
	public static final String C_SERVICE_LOADER = "service-loader.";
	public static final String C_NAMED_CLASS = "named-class.";
	public static final String C_BUILTIN_SCRIPTING = "builtin-scripting.";
	public static final String C_NEST_REPOSITORY = "nest-repository.";

	public static final String E_ILLEGAL = "illegal";
	public static final String E_VERSION_FORMAT = "version-format";
	public static final String E_PROTOCOL = "protocol";
	public static final String E_FORMAT = "format";
	public static final String E_RELATIVE = "relative";
	public static final String E_RESERVED = "reserved";
	public static final String E_MISSING = "missing";
	public static final String E_INVALID_ROOT = "invalid-root";
	public static final String E_DUPLICATE = "duplicate";
	public static final String E_ROOT_NOT_FOUND = "root-not-found";
	public static final String E_DUPLICATE_KEY = "duplicate-key";
	public static final String E_MISSING_DAEMON = "missing-daemon";
	public static final String E_INVALID_KEY = "invalid-key";
	public static final String E_INVALID_COMBINATION = "invalid-combination";

	private static final AtomicReferenceFieldUpdater<SakerIDEProject, ProjectCacheHandle> ARFU_retrievedProjectHandle = AtomicReferenceFieldUpdater
			.newUpdater(SakerIDEProject.class, ProjectCacheHandle.class, "retrievedProjectHandle");

	private final SakerIDEPlugin plugin;
	private Path projectPath;

	private final Lock configurationChangeLock = ThreadUtils.newExclusiveLock();
	private ProjectIDEConfigurationCollection configurationCollection;
	private ValidatedProjectProperties ideProjectProperties;
	private volatile ProjectCacheHandle retrievedProjectHandle;

	private final Object scriptEnvironmentAccessLock = new Object();
	private BasicScriptModellingEnvironment scriptingEnvironment;
	private IDEProjectProperties scriptingEnvironmentConfigurationProperties;

	private LazySupplier<SakerExecutionCache> scriptingEnvironmentExecutionCache;

	private final Set<ExceptionDisplayer> exceptionDisplayers = Collections
			.synchronizedSet(ObjectUtils.newSetFromMap(new WeakHashMap<>()));

	private ThreadedResourceLoader<Map<String, ? extends BuildRepository>> scriptingBuildRepositoryLoader = new ThreadedResourceLoader<>();

	private final Set<ProjectResourceListener> projectResourceListeners = Collections
			.synchronizedSet(ObjectUtils.newSetFromMap(new WeakHashMap<>()));

	private final PluginResourceListener pluginResourceListener = new PluginResourceListener() {
		@Override
		public void environmentClosing(SakerEnvironmentImpl environment) {
			notifyScriptModellingAboutEnvironmentClosing(environment);
		}

		@Override
		public void environmentCreated(SakerEnvironmentImpl environment) {
			notifyScriptModellingAboutEnvironmentCreated(environment);
		}

	};

	protected SakerIDEProject(SakerIDEPlugin plugin) {
		this.plugin = plugin;
		this.scriptingEnvironmentExecutionCache = createScriptingEnvironmentExecutionCacheSupplier();
		plugin.addPluginResourceListener(pluginResourceListener);
	}

	private LazySupplier<SakerExecutionCache> createScriptingEnvironmentExecutionCacheSupplier() {
		return LazySupplier.of(() -> {
			SakerEnvironmentImpl pluginenv;
			try {
				pluginenv = plugin.getPluginEnvironment();
			} catch (IOException e) {
				return null;
			}
			if (pluginenv == null) {
				return null;
			}
			return new RepositoryLoadExceptionHandlingSakerExecutionCache(pluginenv);
		});
	}

	private LazySupplier<SakerExecutionCache> createScriptingEnvironmentExecutionCacheSupplier(
			SakerEnvironmentImpl environment) {
		if (environment == null) {
			return LazySupplier.of(Functionals.nullSupplier());
		}
		return LazySupplier.of(() -> new RepositoryLoadExceptionHandlingSakerExecutionCache(environment));
	}

	public Path getProjectPath() {
		return projectPath;
	}

	public void addExceptionDisplayer(ExceptionDisplayer displayer) {
		if (displayer == null) {
			return;
		}
		exceptionDisplayers.add(displayer);
	}

	public void removeExceptionDisplayer(ExceptionDisplayer displayer) {
		if (displayer == null) {
			return;
		}
		exceptionDisplayers.remove(displayer);
	}

	public void addProjectResourceListener(ProjectResourceListener listener) {
		if (listener == null) {
			return;
		}
		projectResourceListeners.add(listener);
	}

	public void removeProjectResourceListener(ProjectResourceListener listener) {
		if (listener == null) {
			return;
		}
		projectResourceListeners.remove(listener);
	}

	public void initialize(Path projectpath) {
		if (this.projectPath != null) {
			throw new IllegalStateException("Project already initialized.");
		}
		Objects.requireNonNull(projectpath, "project path");
		this.projectPath = projectpath;

		this.ideProjectProperties = createValidatedProjectProperties(SimpleIDEProjectProperties.getDefaultsInstance());
		this.configurationCollection = new ProjectIDEConfigurationCollection();

		readIDEProjectPropertiesFile();
		readIDEConfigsFile();
	}

	public boolean isInitialized() {
		return this.projectPath != null;
	}

	private static final int VALIDATION_CLASSPATHLOCATION_SCRIPTING = 1;
	private static final int VALIDATION_CLASSPATHLOCATION_REPOSITORY = 2;

	public static Set<PropertiesValidationErrorResult> validateProjectProperties(IDEProjectProperties properties) {
		Set<PropertiesValidationErrorResult> errors = new LinkedHashSet<>();
		validatePropertiesWorkingDirectory(properties, errors);
		validatePropertiesBuildDirectory(properties, errors);
		validatePropertiesMirrorDirectory(properties, errors);

		validatePropertiesUserParameters(properties, errors);
		validatePropertiesRepositories(properties, errors);
		validatePropertiesConnections(properties, errors);
		validatePropertiesMounts(properties, errors);
		validatePropertiesBuildTraceOutput(properties, errors);
		validatePropertiesExecutionDaemonConnectionName(properties, errors);
		validatePropertiesScriptConfigurations(properties, errors);
		validatePropertiesScriptModellingExclusions(properties, errors);
		return errors;
	}

	//can be only called with a copied properties
	private static ValidatedProjectProperties createValidatedProjectProperties(IDEProjectProperties properties) {
		return new ValidatedProjectProperties(properties, validateProjectProperties(properties));
	}

	private static void validatePropertiesRepositories(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		Set<? extends RepositoryIDEProperty> repositories = properties.getRepositories();
		if (repositories != null) {
			Set<String> identifiers = new TreeSet<>();
			for (RepositoryIDEProperty repoprop : repositories) {
				String identifier = repoprop.getRepositoryIdentifier();
				if (!ObjectUtils.isNullOrEmpty(identifier)) {
					if (!identifiers.add(identifier)) {
						errors.add(new PropertiesValidationErrorResult(
								NS_REPOSITORY_CONFIGURATION + C_IDENTIFIER + E_DUPLICATE, repoprop));
					}
				}
				ClassPathServiceEnumeratorIDEProperty serviceenumerator = repoprop.getServiceEnumerator();
				ClassPathLocationIDEProperty classpathlocation = repoprop.getClassPathLocation();
				validatePropertiesClassPathLocation(properties, repoprop, classpathlocation,
						NS_REPOSITORY_CONFIGURATION + C_CLASSPATH, errors, VALIDATION_CLASSPATHLOCATION_REPOSITORY);
				validatePropertiesServiceEnumerator(repoprop, serviceenumerator,
						NS_REPOSITORY_CONFIGURATION + C_SERVICE, errors, VALIDATION_CLASSPATHLOCATION_REPOSITORY);

				if (!isValidClassPathServiceCombination(serviceenumerator, classpathlocation)) {
					errors.add(new PropertiesValidationErrorResult(
							NS_REPOSITORY_CONFIGURATION + C_CLASSPATH + C_SERVICE + E_INVALID_COMBINATION, repoprop));
				}
			}
		}
	}

	private static void validatePropertiesConnections(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		Set<? extends DaemonConnectionIDEProperty> connections = properties.getConnections();
		if (connections != null) {
			Set<String> connectionnames = new TreeSet<>();
			for (DaemonConnectionIDEProperty connprop : connections) {
				String address = connprop.getNetAddress();
				if (ObjectUtils.isNullOrEmpty(address)) {
					errors.add(new PropertiesValidationErrorResult(NS_DAEMON_CONNECTION + C_ADDRESS + E_MISSING,
							connprop));
				}
				String connectionname = connprop.getConnectionName();
				if (ObjectUtils.isNullOrEmpty(connectionname)) {
					errors.add(
							new PropertiesValidationErrorResult(NS_DAEMON_CONNECTION + C_NAME + E_MISSING, connprop));
				} else {
					if (DaemonConnectionIDEProperty.isReservedConnectionName(connectionname)) {
						errors.add(new PropertiesValidationErrorResult(NS_DAEMON_CONNECTION + C_NAME + E_RESERVED,
								connprop));
					}
					if (!DaemonConnectionIDEProperty.isValidConnectionNameFormat(connectionname)) {
						errors.add(new PropertiesValidationErrorResult(NS_DAEMON_CONNECTION + C_NAME + E_FORMAT,
								connprop));
					}
					if (!connectionnames.add(connectionname)) {
						errors.add(new PropertiesValidationErrorResult(NS_DAEMON_CONNECTION + C_NAME + E_DUPLICATE,
								connprop));
					}
				}
			}
		}
	}

	private static void validatePropertiesMounts(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		Set<? extends ProviderMountIDEProperty> mounts = properties.getMounts();
		if (mounts != null) {
			Set<? extends DaemonConnectionIDEProperty> connections = properties.getConnections();
			Set<String> roots = new TreeSet<>();
			for (ProviderMountIDEProperty mountprop : mounts) {
				String rootstr = mountprop.getRoot();
				String clientname = mountprop.getMountClientName();
				String mountpathstr = mountprop.getMountPath();

				if (ObjectUtils.isNullOrEmpty(rootstr)) {
					errors.add(new PropertiesValidationErrorResult(NS_PROVIDER_MOUNT + C_ROOT + E_MISSING, mountprop));
				} else {
					String normalizedroot = SakerIDESupportUtils.tryNormalizePathRoot(rootstr);
					if (ObjectUtils.isNullOrEmpty(normalizedroot)) {
						errors.add(
								new PropertiesValidationErrorResult(NS_PROVIDER_MOUNT + C_ROOT + E_FORMAT, mountprop));
					} else {
						if (!roots.add(normalizedroot)) {
							errors.add(new PropertiesValidationErrorResult(NS_PROVIDER_MOUNT + C_ROOT + E_DUPLICATE,
									mountprop));
						}
					}
				}
				validateMountPath(errors, connections, mountprop, clientname, mountpathstr, NS_PROVIDER_MOUNT);
			}
		}
	}

	private static void validateMountPath(Collection<PropertiesValidationErrorResult> errors,
			Set<? extends DaemonConnectionIDEProperty> connections, Object mountprop, String clientname,
			String mountpathstr, String ns) {
		if (MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname) || MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
			//these are fine
		} else {
			DaemonConnectionIDEProperty connprop = getDaemonConnectionPropertyForConnectionName(connections,
					clientname);
			if (connprop == null) {
				errors.add(new PropertiesValidationErrorResult(ns + C_CLIENT + E_MISSING, mountprop));
			}
		}
		if (mountpathstr == null) {
			errors.add(new PropertiesValidationErrorResult(ns + C_PATH + E_MISSING, mountprop));
		} else {
			try {
				SakerPath mountpath = SakerPath.valueOf(mountpathstr);
				if (MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
					//relative mount path is allowed if it is project relative
					if (mountpath.isAbsolute() && !SakerPath.ROOT_SLASH.equals(mountpath.getRoot())) {
						errors.add(new PropertiesValidationErrorResult(ns + C_PATH + E_INVALID_ROOT, mountprop));
					}
				} else if (!mountpath.isAbsolute()) {
					//if not project relative, the mount path must be absolute
					errors.add(new PropertiesValidationErrorResult(ns + C_PATH + E_RELATIVE, mountprop));
				}
			} catch (RuntimeException e) {
				errors.add(new PropertiesValidationErrorResult(ns + C_PATH + E_FORMAT, mountprop));
			}
		}
	}

	private static void validatePropertiesBuildTraceOutput(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		MountPathIDEProperty btoutprop = properties.getBuildTraceOutput();
		if (btoutprop == null) {
			return;
		}
		String clientname = btoutprop.getMountClientName();
		String mountpathstr = btoutprop.getMountPath();
		if (ObjectUtils.isNullOrEmpty(clientname) && ObjectUtils.isNullOrEmpty(mountpathstr)) {
			//both are null
			return;
		}
		validateMountPath(errors, properties.getConnections(), btoutprop, clientname, mountpathstr, NS_BUILD_TRACE_OUT);
	}

	private static void validatePropertiesExecutionDaemonConnectionName(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		String executiondaemonconnectionname = properties.getExecutionDaemonConnectionName();
		if (ObjectUtils.isNullOrEmpty(executiondaemonconnectionname)) {
			return;
		}
		DaemonConnectionIDEProperty execdaemonconnprop = getDaemonConnectionPropertyForConnectionName(
				properties.getConnections(), executiondaemonconnectionname);
		if (execdaemonconnprop == null) {
			errors.add(new PropertiesValidationErrorResult(NS_EXECUTION_DAEMON_NAME + E_MISSING_DAEMON,
					executiondaemonconnectionname));
		}
	}

	private static void validatePropertiesUserParameters(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		Set<? extends Entry<String, String>> userparameters = properties.getUserParameters();
		if (userparameters == null) {
			return;
		}
		Set<String> keys = new TreeSet<>();
		for (Entry<String, String> entry : userparameters) {
			String key = entry.getKey();
			if (ObjectUtils.isNullOrEmpty(key)) {
				errors.add(new PropertiesValidationErrorResult(NS_USER_PARAMETERS + E_INVALID_KEY, key));
				continue;
			}
			if (!keys.add(key)) {
				errors.add(new PropertiesValidationErrorResult(NS_USER_PARAMETERS + E_DUPLICATE_KEY, key));
			}
		}
	}

	private static void validatePropertiesScriptConfigurations(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigurations = properties.getScriptConfigurations();
		if (scriptconfigurations == null) {
			return;
		}
		Set<WildcardPath> wildcards = new TreeSet<>();
		for (ScriptConfigurationIDEProperty scriptconfigprop : scriptconfigurations) {
			String scriptwc = scriptconfigprop.getScriptsWildcard();
			if (ObjectUtils.isNullOrEmpty(scriptwc)) {
				errors.add(new PropertiesValidationErrorResult(NS_SCRIPT_CONFIGURATION + C_WILDCARD + E_MISSING,
						scriptconfigprop));
			} else {
				try {
					WildcardPath wildcard = WildcardPath.valueOf(scriptwc);
					if (!wildcards.add(wildcard)) {
						errors.add(new PropertiesValidationErrorResult(
								NS_SCRIPT_CONFIGURATION + C_WILDCARD + E_DUPLICATE, scriptconfigprop));
					}
				} catch (RuntimeException e) {
					errors.add(new PropertiesValidationErrorResult(NS_SCRIPT_CONFIGURATION + C_WILDCARD + E_FORMAT,
							scriptconfigprop));
				}
			}
			ClassPathLocationIDEProperty classpathlocation = scriptconfigprop.getClassPathLocation();
			ClassPathServiceEnumeratorIDEProperty serviceenumerator = scriptconfigprop.getServiceEnumerator();

			validatePropertiesStringKeyValueOptions(scriptconfigprop, scriptconfigprop.getScriptOptions(),
					NS_SCRIPT_CONFIGURATION + C_OPTIONS, errors);
			validatePropertiesClassPathLocation(properties, scriptconfigprop, classpathlocation,
					NS_SCRIPT_CONFIGURATION + C_CLASSPATH, errors, VALIDATION_CLASSPATHLOCATION_SCRIPTING);
			validatePropertiesServiceEnumerator(scriptconfigprop, serviceenumerator,
					NS_SCRIPT_CONFIGURATION + C_SERVICE, errors, VALIDATION_CLASSPATHLOCATION_SCRIPTING);

			if (!isValidClassPathServiceCombination(serviceenumerator, classpathlocation)) {
				errors.add(new PropertiesValidationErrorResult(
						NS_SCRIPT_CONFIGURATION + C_CLASSPATH + C_SERVICE + E_INVALID_COMBINATION, scriptconfigprop));
			}
		}
	}

	private static void validatePropertiesServiceEnumerator(Object property,
			ClassPathServiceEnumeratorIDEProperty serviceenumerator, String errornamespace,
			Collection<PropertiesValidationErrorResult> errors, int validation) {
		if (serviceenumerator == null) {
			errors.add(new PropertiesValidationErrorResult(errornamespace + E_MISSING, property));
		} else {
			serviceenumerator.accept(new ClassPathServiceEnumeratorIDEProperty.Visitor<Void, Void>() {
				@Override
				public Void visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
					String serviceclass = property.getServiceClass();
					if (ObjectUtils.isNullOrEmpty(serviceclass)) {
						errors.add(new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_SERVICE_LOADER
								+ SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING, property));
					}
					return null;
				}

				@Override
				public Void visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
					String serviceclass = property.getClassName();
					if (ObjectUtils.isNullOrEmpty(serviceclass)) {
						errors.add(new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_NAMED_CLASS
								+ SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING, property));
					}
					return null;
				}

				@Override
				public Void visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
					if (validation != VALIDATION_CLASSPATHLOCATION_SCRIPTING) {
						errors.add(new PropertiesValidationErrorResult(
								errornamespace + SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL,
								property));
					}
					return null;
				}

				@Override
				public Void visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
					if (validation != VALIDATION_CLASSPATHLOCATION_REPOSITORY) {
						errors.add(new PropertiesValidationErrorResult(
								errornamespace + SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL,
								property));
					}
					return null;
				}
			}, null);
		}
	}

	private static final Map<ClassPathServiceEnumeratorIDEProperty, ClassPathLocationIDEProperty> SERVICE_CLASSPATH_COMBINATIONS = new HashMap<>();
	private static final Map<ClassPathLocationIDEProperty, ClassPathServiceEnumeratorIDEProperty> CLASSPATH_SERVICE_COMBINATIONS = new HashMap<>();
	static {
		SERVICE_CLASSPATH_COMBINATIONS.put(new NestRepositoryFactoryServiceEnumeratorIDEProperty(),
				new NestRepositoryClassPathLocationIDEProperty());
		SERVICE_CLASSPATH_COMBINATIONS.put(BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE,
				BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE);

		CLASSPATH_SERVICE_COMBINATIONS.put(new NestRepositoryClassPathLocationIDEProperty(),
				new NestRepositoryFactoryServiceEnumeratorIDEProperty());
		CLASSPATH_SERVICE_COMBINATIONS.put(BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE,
				BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE);
	}

	private static boolean isValidClassPathServiceCombination(ClassPathServiceEnumeratorIDEProperty service,
			ClassPathLocationIDEProperty classpath) {
		if (service == null && classpath == null) {
			//other errors will be issued for missing classpaths, no need to consider both nulls invalid
			return true;
		}
		ClassPathLocationIDEProperty expectedcp = SERVICE_CLASSPATH_COMBINATIONS.get(service);
		if (expectedcp != null) {
			if (!expectedcp.equals(classpath)) {
				return false;
			}
		}
		ClassPathServiceEnumeratorIDEProperty expectedservice = CLASSPATH_SERVICE_COMBINATIONS.get(classpath);
		if (expectedservice != null) {
			if (!expectedservice.equals(service)) {
				return false;
			}
		}
		return true;
	}

	private static void validatePropertiesClassPathLocation(IDEProjectProperties properties, Object property,
			ClassPathLocationIDEProperty classpathlocation, String errornamespace,
			Collection<PropertiesValidationErrorResult> errors, int validation) {
		if (classpathlocation == null) {
			errors.add(new PropertiesValidationErrorResult(errornamespace + E_MISSING, property));
		} else {
			classpathlocation.accept(new ClassPathLocationIDEProperty.Visitor<Void, Void>() {
				@Override
				public Void visit(JarClassPathLocationIDEProperty property, Void param) {
					String connname = property.getConnectionName();
					if (MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(connname)
							|| MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(connname)) {
						//these are fine
					} else {
						DaemonConnectionIDEProperty connprop = getDaemonConnectionPropertyForConnectionName(
								properties.getConnections(), connname);
						if (connprop == null) {
							errors.add(new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_JAR
									+ SakerIDEProject.C_CONNECTION + SakerIDEProject.E_MISSING_DAEMON, property));
						}
					}
					String jarpath = property.getJarPath();
					if (ObjectUtils.isNullOrEmpty(jarpath)) {
						errors.add(new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_JAR
								+ SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING, property));
					} else {
						try {
							SakerPath.valueOf(jarpath);
						} catch (RuntimeException e) {
							errors.add(new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_JAR
									+ SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT, property));
						}
					}
					return null;
				}

				@Override
				public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
					String urlstr = property.getUrl();
					if (ObjectUtils.isNullOrEmpty(urlstr)) {
						errors.add(new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_HTTP_URL_JAR
								+ SakerIDEProject.C_URL + SakerIDEProject.E_MISSING, property));
					} else {
						try {
							URL url = new URL(urlstr);
							String protocol = url.getProtocol();
							if (!"http".equals(protocol) && !"https".equals(protocol)) {
								errors.add(new PropertiesValidationErrorResult(
										errornamespace + SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL
												+ SakerIDEProject.E_PROTOCOL,
										property));
							}
						} catch (MalformedURLException e) {
							errors.add(
									new PropertiesValidationErrorResult(errornamespace + SakerIDEProject.C_HTTP_URL_JAR
											+ SakerIDEProject.C_URL + SakerIDEProject.E_FORMAT, property));
						}
					}
					return null;
				}

				@Override
				public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
					if (validation != VALIDATION_CLASSPATHLOCATION_SCRIPTING) {
						errors.add(new PropertiesValidationErrorResult(
								errornamespace + SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL,
								property));
					}
					return null;
				}

				@Override
				public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
					if (validation != VALIDATION_CLASSPATHLOCATION_REPOSITORY) {
						errors.add(new PropertiesValidationErrorResult(
								errornamespace + SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL,
								property));
					} else {
						String ver = property.getVersion();
						if (ver != null) {
							if (!NestRepositoryClassPathLocationIDEProperty.isValidVersionNumber(ver)) {
								errors.add(new PropertiesValidationErrorResult(errornamespace
										+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_VERSION_FORMAT,
										property));
							}
						}
					}
					return null;
				}
			}, null);
		}
	}

	private static void validatePropertiesStringKeyValueOptions(Object property,
			Set<? extends Entry<String, String>> options, String errornamespace,
			Collection<PropertiesValidationErrorResult> errors) {
		if (options == null) {
			return;
		}
		Set<String> keys = new TreeSet<>();
		for (Entry<String, String> entry : options) {
			String key = entry.getKey();
			if (ObjectUtils.isNullOrEmpty(key)) {
				errors.add(new PropertiesValidationErrorResult(errornamespace + E_INVALID_KEY,
						new Object[] { property, key }));
				continue;
			}
			if (!keys.add(key)) {
				errors.add(new PropertiesValidationErrorResult(errornamespace + E_DUPLICATE_KEY,
						new Object[] { property, key }));
			}
		}
	}

	private static void validatePropertiesScriptModellingExclusions(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		Set<String> scriptmodellingexclusions = properties.getScriptModellingExclusions();
		if (scriptmodellingexclusions != null) {
			Set<WildcardPath> wildcards = new TreeSet<>();
			for (String excl : scriptmodellingexclusions) {
				try {
					WildcardPath wc = WildcardPath.valueOf(excl);
					if (!wildcards.add(wc)) {
						errors.add(
								new PropertiesValidationErrorResult(NS_SCRIPT_MODELLING_EXCLUSION + E_DUPLICATE, excl));
					}
				} catch (RuntimeException e) {
					//failed to parse
					errors.add(new PropertiesValidationErrorResult(NS_SCRIPT_MODELLING_EXCLUSION + E_FORMAT, excl));
				}
			}
		}
	}

	private static void validatePropertiesBuildDirectory(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		String builddir = properties.getBuildDirectory();
		if (ObjectUtils.isNullOrEmpty(builddir)) {
			return;
		}
		SakerPath buildpath;
		try {
			buildpath = SakerPath.valueOf(builddir);
		} catch (RuntimeException e) {
			errors.add(new PropertiesValidationErrorResult(NS_BUILD_DIRECTORY + E_FORMAT, builddir));
			return;
		}
		if (buildpath.isAbsolute()) {
			validatePropertiesPathRequireExecutionRoot(properties, buildpath, NS_BUILD_DIRECTORY, errors);
		}
	}

	private static void validatePropertiesMirrorDirectory(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		String mirrordir = properties.getMirrorDirectory();
		if (ObjectUtils.isNullOrEmpty(mirrordir)) {
			return;
		}
		SakerPath mirrorpath;
		try {
			mirrorpath = SakerPath.valueOf(mirrordir);
		} catch (RuntimeException e) {
			errors.add(new PropertiesValidationErrorResult(NS_MIRROR_DIRECTORY + E_FORMAT, mirrordir));
			return;
		}
		if (!mirrorpath.isAbsolute()) {
			errors.add(new PropertiesValidationErrorResult(NS_MIRROR_DIRECTORY + E_RELATIVE, mirrorpath));
			return;
		}
	}

	private static void validatePropertiesWorkingDirectory(IDEProjectProperties properties,
			Collection<PropertiesValidationErrorResult> errors) {
		String workingdir = properties.getWorkingDirectory();
		if (ObjectUtils.isNullOrEmpty(workingdir)) {
			errors.add(new PropertiesValidationErrorResult(NS_WORKING_DIRECTORY + E_MISSING, null));
			return;
		}
		SakerPath workingpath;
		try {
			workingpath = SakerPath.valueOf(workingdir);
		} catch (RuntimeException e) {
			errors.add(new PropertiesValidationErrorResult(NS_WORKING_DIRECTORY + E_FORMAT, workingdir));
			return;
		}
		if (!workingpath.isAbsolute()) {
			errors.add(new PropertiesValidationErrorResult(NS_WORKING_DIRECTORY + E_RELATIVE, workingpath));
			return;
		}
		validatePropertiesPathRequireExecutionRoot(properties, workingpath, NS_WORKING_DIRECTORY, errors);
	}

	private static boolean validatePropertiesPathRequireExecutionRoot(IDEProjectProperties properties, SakerPath path,
			String errornamespace, Collection<PropertiesValidationErrorResult> errors) {
		Set<? extends ProviderMountIDEProperty> mounts = properties.getMounts();
		if (mounts != null) {
			String root = SakerIDESupportUtils.tryNormalizePathRoot(path.getRoot());
			if (root != null) {
				for (ProviderMountIDEProperty mount : mounts) {
					if (root.equals(SakerIDESupportUtils.normalizePathRoot(mount.getRoot()))) {
						//found root
						return true;
					}
				}
			}
		}
		errors.add(new PropertiesValidationErrorResult(errornamespace + E_ROOT_NOT_FOUND, path));
		return false;
	}

	private void readIDEConfigsFile() {
		try (InputStream is = Files.newInputStream(projectPath.resolve(IDE_CONFIG_FILE_NAME))) {
			XMLStructuredReader reader = new XMLStructuredReader(is);
			try (StructuredObjectInput objreader = reader.readObject(IDE_CONFIG_FILE_ROOT_OBJECT_NAME)) {
				List<IDEConfiguration> configs = new ArrayList<>();
				try (StructuredArrayObjectInput ideconfigsreader = objreader.readArray("ide-configurations")) {
					if (ideconfigsreader != null) {
						int len = ideconfigsreader.length();
						for (int i = 0; i < len; i++) {
							try (StructuredObjectInput obj = ideconfigsreader.readObject()) {
								if (obj != null) {
									SimpleIDEConfiguration config = IDEPersistenceUtils.readIDEConfiguration(obj);
									if (config != null) {
										configs.add(config);
									}
								}
							}
						}
					}
				}
				this.configurationCollection = new ProjectIDEConfigurationCollection(configs);
			}
		} catch (IOException e) {
		}
	}

	public static IDEProjectProperties readIDEProjectPropertiesFile(Path projectPath) throws IOException {
		try (InputStream is = Files.newInputStream(projectPath.resolve(PROPERTIES_FILE_NAME))) {
			XMLStructuredReader reader = new XMLStructuredReader(is);
			try (StructuredObjectInput objreader = reader.readObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
				try (StructuredObjectInput propertiesreader = objreader.readObject("project-properties")) {
					if (propertiesreader != null) {
						return IDEPersistenceUtils.readIDEProjectProperties(propertiesreader);
					}
				}
			}
		}
		return null;
	}

	private void readIDEProjectPropertiesFile() {
		try {
			IDEProjectProperties properties = readIDEProjectPropertiesFile(this.projectPath);
			if (properties != null) {
				this.ideProjectProperties = createValidatedProjectProperties(properties);
			}
		} catch (IOException e) {
		}
	}

	private void persistIDEConfigsFile() throws IOException {
		Path propfilepath = projectPath.resolve(IDE_CONFIG_FILE_NAME);
		Path tempfilepath = propfilepath.resolveSibling(propfilepath.getFileName() + "." + UUID.randomUUID() + ".temp");
		try (OutputStream os = Files.newOutputStream(tempfilepath);
				XMLStructuredWriter writer = new XMLStructuredWriter(os);
				StructuredObjectOutput objwriter = writer.writeObject(IDE_CONFIG_FILE_ROOT_OBJECT_NAME)) {
			try (StructuredArrayObjectOutput ideconfigsarraywriter = objwriter.writeArray("ide-configurations")) {
				for (IDEConfiguration ideconfig : configurationCollection.getConfigurations()) {
					try (StructuredObjectOutput ideconfigobjwriter = ideconfigsarraywriter.writeObject()) {
						IDEPersistenceUtils.writeIDEConfiguration(ideconfigobjwriter, ideconfig);
					}
				}
			}
		}
		Files.move(tempfilepath, propfilepath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void persistIDEProjectPropertiesFile() throws IOException {
		Path propfilepath = projectPath.resolve(PROPERTIES_FILE_NAME);
		Path tempfilepath = propfilepath.resolveSibling(propfilepath.getFileName() + "." + UUID.randomUUID() + ".temp");
		try (OutputStream os = Files.newOutputStream(tempfilepath);
				XMLStructuredWriter writer = new XMLStructuredWriter(os);
				StructuredObjectOutput objwriter = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
			try (StructuredObjectOutput propertieswriter = objwriter.writeObject("project-properties")) {
				IDEPersistenceUtils.writeIDEProjectProperties(propertieswriter, ideProjectProperties.properties);
			}
		}
		Files.move(tempfilepath, propfilepath, StandardCopyOption.REPLACE_EXISTING);
	}

	public final NavigableSet<SakerPath> getTrackedScriptPaths() {
		ScriptModellingEnvironment scriptenv;
		try {
			scriptenv = getScriptingEnvironment();
		} catch (IOException e) {
			return Collections.emptyNavigableSet();
		}
		if (scriptenv == null) {
			return Collections.emptyNavigableSet();
		}
		return scriptenv.getTrackedScriptPaths();
	}

	//doc: returns null if script is not part of the script configuration
	public final Set<String> getScriptTargets(SakerPath scriptpath) throws ScriptParsingFailedException, IOException {
		ScriptModellingEnvironment scriptenv = getScriptingEnvironment();
		if (scriptenv == null) {
			return null;
		}
		ScriptSyntaxModel model = scriptenv.getModel(scriptpath);
		if (model != null) {
			return model.getTargetNames();
		}
		return null;
	}

	private void notifyScriptModellingAboutEnvironmentClosing(SakerEnvironmentImpl env) {
		synchronized (scriptEnvironmentAccessLock) {
			if (scriptingEnvironment == null) {
				return;
			}
			callListeners(ImmutableUtils.makeImmutableList(projectResourceListeners),
					l -> l.scriptModellingEnvironmentClosing(scriptingEnvironment));
			this.scriptingEnvironmentExecutionCache = LazySupplier.of(Functionals.nullSupplier());
			try {
				scriptingBuildRepositoryLoader.close();
			} catch (InterruptedException e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to close scripting build repository.", e);
			}
			scriptingBuildRepositoryLoader = new ThreadedResourceLoader<>();
		}
	}

	private void notifyScriptModellingAboutEnvironmentCreated(SakerEnvironmentImpl environment) {
		synchronized (scriptEnvironmentAccessLock) {
			if (scriptingEnvironment == null) {
				return;
			}
			IOException e = IOUtils.closeExc(scriptingEnvironment);
			scriptingEnvironment = null;
			if (e != null) {
				displayException(SakerLog.SEVERITY_WARNING, "Failed to close scripting environment.", e);
			}
			this.scriptingEnvironmentExecutionCache = createScriptingEnvironmentExecutionCacheSupplier(environment);
			BasicScriptModellingEnvironment nenv = createScriptingEnvironmentLocked(environment);
			scriptingEnvironment = nenv;
			if (nenv != null) {
				callListeners(ImmutableUtils.makeImmutableList(projectResourceListeners),
						l -> l.scriptModellingEnvironmentCreated(nenv));
			}
		}
	}

	public final ScriptModellingEnvironment getScriptingEnvironment() throws IOException {
		SakerEnvironmentImpl pluginenv = plugin.getPluginEnvironment();
		synchronized (scriptEnvironmentAccessLock) {
			return getScriptingEnvironmentLocked(pluginenv);
		}
	}

	private ScriptModellingEnvironment getScriptingEnvironmentLocked(SakerEnvironmentImpl environment) {
		if (scriptingEnvironment == null) {
			return scriptingEnvironment = createScriptingEnvironmentLocked(environment);
		}
		return scriptingEnvironment;
	}

	private BasicScriptModellingEnvironment createScriptingEnvironmentLocked(SakerEnvironmentImpl environment) {
		ValidatedProjectProperties properties = this.ideProjectProperties;
		try {
			SimpleScriptModellingEnvironmentConfiguration scriptenvconfig;
			try {
				scriptenvconfig = createScriptEnvironmentConfiguration(properties.properties);
				scriptingEnvironmentConfigurationProperties = properties.properties;
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to create script environment configuration.", e);
				//TODO create some default script configuration so we can create the modelling environment
				return null;
			}
			//Note: we could override createModelForPath and display any exceptions in regards
			//    with retrieving the model, however, that is detrimental to user experience
			//    we don't want to spam the user with exceptions regarding the script modelling
			return new BasicScriptModellingEnvironment(plugin, scriptenvconfig, environment);
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to create scripting environment.", e);
			return null;
		}
	}

	public final SakerIDEPlugin getPlugin() {
		return plugin;
	}

	protected void close() throws IOException {
		synchronized (this) {
			plugin.removePluginResourceListener(pluginResourceListener);
			IOException exc = null;
			ProjectCacheHandle projecthandle = ARFU_retrievedProjectHandle.getAndSet(this, null);
			if (projecthandle != null) {
				try {
					projecthandle.reset();
				} catch (RMIRuntimeException e) {
					//ignore
				}
			}
			exc = IOUtils.closeExc(exc, scriptingEnvironmentExecutionCache.getIfComputedPrevent());
			synchronized (scriptEnvironmentAccessLock) {
				exc = IOUtils.closeExc(exc, scriptingEnvironment);
				scriptingEnvironment = null;
				try {
					scriptingBuildRepositoryLoader.close();
				} catch (InterruptedException e) {
					exc = IOUtils.addExc(exc, e);
				}
			}
			IOUtils.throwExc(exc);
		}
	}

	public void displayException(int severity, String message, Throwable e) {
		int callcount = callListeners(ImmutableUtils.makeImmutableList(exceptionDisplayers),
				d -> d.displayException(severity, message, e));
		if (e != null) {
			if (callcount == 0) {
				e.printStackTrace();
			}
		}
	}

	private <L> int callListeners(Iterable<L> listeners, Consumer<? super L> caller) {
		int c = 0;
		for (L l : listeners) {
			++c;
			try {
				caller.accept(l);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_WARNING,
						"Failed to call event listener: " + ObjectUtils.classNameOf(l), e);
			}
		}
		return c;
	}

	public final BuildTaskExecutionResult build(SakerPath scriptfile, String targetname, DaemonEnvironment daemonenv,
			ExecutionParametersImpl parameters) {
		if (daemonenv == null) {
			throw new NullPointerException("Daemon environment is not available.");
		}
		ProjectCacheHandle project;
		try {
			ExecutionPathConfiguration pathconfig = parameters.getPathConfiguration();
			PathKey pathkey = pathconfig.getPathKey(pathconfig.getWorkingDirectory());
			project = getProjectHandle(pathkey, daemonenv);
		} catch (IOException e) {
			return BuildTaskExecutionResultImpl.createInitializationFailed(e);
		}
		BuildExecutionInvoker environment = daemonenv.getExecutionInvoker();
		return environment.run(scriptfile, targetname, parameters, project);
	}

	public final DaemonEnvironment getExecutionDaemonEnvironment(IDEProjectProperties projprops) throws IOException {
		if (projprops != null) {
			String daemonconnname = projprops.getExecutionDaemonConnectionName();
			if (!ObjectUtils.isNullOrEmpty(daemonconnname)) {
				return getDaemonConnectionWithName(daemonconnname, projprops).getDaemonEnvironment();
			}
		}
		return plugin.getPluginDaemonEnvironment();
	}

	public final void clean() throws IOException, InterruptedException {
		IDEProjectProperties properties = this.ideProjectProperties.properties;
		String builddir = properties.getBuildDirectory();
		if (!ObjectUtils.isNullOrEmpty(builddir)) {
			//TODO do not create full path configuration, but only locate the build directory
			ExecutionPathConfiguration pathconfig = createPathConfiguration(properties);
			ProviderHolderPathKey buildpathkey = pathconfig.getPathKey(SakerPath.valueOf(builddir));
			buildpathkey.getFileProvider().clearDirectoryRecursively(buildpathkey.getPath());
			ProviderHolderPathKey pathkey = pathconfig.getPathKey(pathconfig.getWorkingDirectory());
			ProjectCacheHandle projecthandle = getProjectHandle(pathkey, getExecutionDaemonEnvironment(properties));
			projecthandle.clean();
		}
		//clear IDE configurations
		setProjectIDEConfigurationCollection(new ProjectIDEConfigurationCollection());
	}

	private ProjectCacheHandle getProjectHandle(PathKey pathkey, DaemonEnvironment daemonenv) throws IOException {
		ProjectCacheHandle project = daemonenv.getProject(pathkey);
		ProjectCacheHandle prevproject = ARFU_retrievedProjectHandle.getAndSet(this, project);
		if (prevproject != null && prevproject != project) {
			prevproject.reset();
		}
		return project;
	}

	public final ProjectIDEConfigurationCollection getProjectIDEConfigurationCollection() {
		return configurationCollection;
	}

	public final void setProjectIDEConfigurationCollection(ProjectIDEConfigurationCollection configurationCollection)
			throws IOException {
		if (configurationCollection == null) {
			configurationCollection = new ProjectIDEConfigurationCollection();
		}
		configurationChangeLock.lock();
		try {
			if (configurationCollection.equals(this.configurationCollection)) {
				return;
			}
			persistIDEConfigsFile();
			this.configurationCollection = configurationCollection;
		} finally {
			configurationChangeLock.unlock();
		}
	}

	public final ExecutionParametersImpl createExecutionParameters(IDEProjectProperties properties)
			throws IOException, PropertiesValidationException, InterruptedException {
		properties = properties == null ? SimpleIDEProjectProperties.getDefaultsInstance()
				: SimpleIDEProjectProperties.copy(properties);
		ValidatedProjectProperties validatedprops = this.ideProjectProperties;
		if (!validatedprops.properties.equals(properties)) {
			validatedprops = createValidatedProjectProperties(properties);
		}
		if (!validatedprops.isValid()) {
			throw new PropertiesValidationException(ImmutableUtils.makeImmutableLinkedHashSet(validatedprops.errors));
		}
		BuildInformation buildinfo = null;
		//XXX don't connect to daemons which are not used as clusters, and not part of the path configuration
		Map<String, RemoteDaemonConnection> daemonconnections;
		String execdaemonnameprop = properties.getExecutionDaemonConnectionName();
		Set<? extends DaemonConnectionIDEProperty> daemonconnectionproperties = properties.getConnections();
		DaemonEnvironment execdaemonenv;
		if (!ObjectUtils.isNullOrEmpty(execdaemonnameprop)) {
			//the execution daemon is not the same as the plugin daemon
			DaemonConnectionIDEProperty execdaemonconnprop = getDaemonConnectionPropertyForConnectionName(
					daemonconnectionproperties, execdaemonnameprop);
			if (execdaemonconnprop == null) {
				throw new IllegalArgumentException(
						"Daemon connection not found for execution daemon name: " + execdaemonnameprop);
			}
			Map<String, RemoteDaemonConnection> execdaemonconnection = plugin
					.connectToDaemonsFromPluginEnvironment(Collections.singleton(execdaemonconnprop));
			if (execdaemonconnection.isEmpty()) {
				throw new IOException("Failed to connect to execution daemon: " + execdaemonnameprop + " at "
						+ execdaemonconnprop.getNetAddress());
			}
			execdaemonenv = execdaemonconnection.values().iterator().next().getDaemonEnvironment();
			daemonconnections = plugin.connectToDaemonsFromDaemonEnvironment(daemonconnectionproperties, execdaemonenv);

			buildinfo = new BuildInformation();
			NavigableMap<String, ConnectionInformation> connectioninfos = new TreeMap<>();
			for (Entry<String, RemoteDaemonConnection> entry : execdaemonconnection.entrySet()) {
				RemoteDaemonConnection connection = entry.getValue();
				ConnectionInformation conninfo = new ConnectionInformation();
				conninfo.setConnectionRootFileProviderUUID(SakerPathFiles
						.getRootFileProviderKey(connection.getDaemonEnvironment().getFileProvider()).getUUID());
				conninfo.setConnectionBuildEnvironmentUUID(
						connection.getDaemonEnvironment().getEnvironmentIdentifier());
				//XXX more efficient lookup
				String connectionname = entry.getKey();
				DaemonConnectionIDEProperty cprop = getDaemonConnectionPropertyForConnectionName(
						daemonconnectionproperties, connectionname);
				conninfo.setConnectionAddress(cprop.getConnectionName());

				connectioninfos.put(connectionname, conninfo);
			}
			buildinfo.setConnectionInformations(connectioninfos);
		} else {
			daemonconnections = plugin.connectToDaemonsFromPluginEnvironment(daemonconnectionproperties);
			execdaemonenv = plugin.getPluginDaemonEnvironment();
		}

		MountPathIDEProperty buildtraceoutprop = properties.getBuildTraceOutput();
		ProviderHolderPathKey buildtraceoutpathkey = null;
		if (buildtraceoutprop != null) {
			String btcname = buildtraceoutprop.getMountClientName();
			String btpath = buildtraceoutprop.getMountPath();
			if (!ObjectUtils.isNullOrEmpty(btcname) && !ObjectUtils.isNullOrEmpty(btpath)) {
				buildtraceoutpathkey = getMountPathPropertyPathKey(buildtraceoutprop, daemonconnections);
			}
		}

		ExecutionPathConfiguration pathconfiguration = createPathConfiguration(properties, daemonconnections);
		ExecutionScriptConfiguration scriptconfiguration = createScriptConfiguration(properties);
		ExecutionRepositoryConfiguration repositoryconfiguration = createRepositoryConfiguration(properties);
		Map<String, String> userparameters = SakerIDEPlugin.entrySetToMap(properties.getUserParameters());

		Collection<TaskInvokerFactory> taskinvokerfactories = new ArrayList<>();
		if (!ObjectUtils.isNullOrEmpty(daemonconnectionproperties)) {
			for (DaemonConnectionIDEProperty connprop : daemonconnectionproperties) {
				String connectionname = connprop.getConnectionName();
				if (connprop.isUseAsCluster()) {
					RemoteDaemonConnection conn = daemonconnections.get(connectionname);
					if (conn == null) {
						throw new IllegalArgumentException("Failed to retrieve daemon connection: " + connectionname);
					}
					TaskInvokerFactory taskinvokerfactory = conn.getClusterTaskInvokerFactory();
					if (taskinvokerfactory == null) {
						throw new IllegalArgumentException(
								"Build daemon doesn't support using it as cluster: " + connectionname);
					}
					taskinvokerfactories.add(taskinvokerfactory);
				}
			}
		}
		if (execdaemonenv != null
				&& SakerIDESupportUtils.getBooleanValueOrDefault(properties.getUseClientsAsClusters(), false)) {
			ObjectUtils.addAll(taskinvokerfactories, execdaemonenv.getClientClusterTaskInvokerFactories());
		}

		ExecutionParametersImpl parameters = new ExecutionParametersImpl();
		parameters.setPathConfiguration(pathconfiguration);
		parameters.setScriptConfiguration(scriptconfiguration);
		parameters.setRepositoryConfiguration(repositoryconfiguration);

		parameters.setUserParameters(userparameters);
		String builddirprop = properties.getBuildDirectory();
		if (!ObjectUtils.isNullOrEmpty(builddirprop)) {
			parameters.setBuildDirectory(SakerPath.valueOf(builddirprop));
		}
		String mirrordirprop = properties.getMirrorDirectory();
		if (!ObjectUtils.isNullOrEmpty(mirrordirprop)) {
			parameters.setMirrorDirectory(SakerPath.valueOf(mirrordirprop));
		}

		parameters.setTaskInvokerFactories(taskinvokerfactories);
		parameters.setBuildInfo(buildinfo);
		parameters.setBuildTraceOutputPathKey(buildtraceoutpathkey);
		parameters.setBuildTraceEmbedArtifacts(
				SakerIDESupportUtils.getBooleanValueOrDefault(properties.getBuildTraceEmbedArtifacts(), false));

		return parameters;
	}

	private static DaemonConnectionIDEProperty getDaemonConnectionPropertyForConnectionName(
			Collection<? extends DaemonConnectionIDEProperty> connections, String name) {
		if (name == null) {
			return null;
		}
		if (ObjectUtils.isNullOrEmpty(connections)) {
			return null;
		}
		for (DaemonConnectionIDEProperty prop : connections) {
			if (name.equals(prop.getConnectionName())) {
				return prop;
			}
		}
		return null;
	}

	public final boolean setIDEProjectProperties(IDEProjectProperties properties) throws IOException {
		properties = properties == null ? SimpleIDEProjectProperties.getDefaultsInstance()
				: SimpleIDEProjectProperties.copy(properties);
		configurationChangeLock.lock();
		try {
			if (properties.equals(this.ideProjectProperties.properties)) {
				return false;
			}
			persistIDEProjectPropertiesFile();
			this.ideProjectProperties = createValidatedProjectProperties(properties);
			return true;
		} finally {
			configurationChangeLock.unlock();
		}
	}

	public void updateForProjectProperties(IDEProjectProperties properties) {
		properties = properties == null ? SimpleIDEProjectProperties.getDefaultsInstance()
				: SimpleIDEProjectProperties.copy(properties);
		synchronized (scriptEnvironmentAccessLock) {
			if (this.scriptingEnvironment == null) {
				return;
			}
			if (Objects.equals(scriptingEnvironmentConfigurationProperties, properties)) {
				return;
			}
			SimpleScriptModellingEnvironmentConfiguration scriptenvconfig;
			try {
				scriptenvconfig = createScriptEnvironmentConfiguration(properties);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to create scripting environment configuration.", e);
				return;
			}
			try {
				scriptingEnvironment.setConfiguration(scriptenvconfig);
				scriptingEnvironmentConfigurationProperties = properties;
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to configure scripting environment.", e);
			}
		}
	}

	public final IDEProjectProperties getIDEProjectProperties() {
		return ideProjectProperties.properties;
	}

	private static Collection<DaemonConnectionIDEProperty> getDaemonConnectionPropertiesForPathConfiguration(
			IDEProjectProperties properties) {
		Set<? extends DaemonConnectionIDEProperty> connections = properties.getConnections();
		if (ObjectUtils.isNullOrEmpty(connections)) {
			return Collections.emptySet();
		}
		Set<? extends ProviderMountIDEProperty> mounts = properties.getMounts();
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return Collections.emptySet();
		}
		Collection<DaemonConnectionIDEProperty> result = new HashSet<>();
		for (ProviderMountIDEProperty mountprop : mounts) {
			String clientname = mountprop.getMountClientName();
			if (ObjectUtils.isNullOrEmpty(clientname)) {
				throw new IllegalArgumentException("Empty client name.");
			}
			if (MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname)
					|| MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
				//these are allowed
				continue;
			}
			if (DaemonConnectionIDEProperty.isReservedConnectionName(clientname)) {
				throw new IllegalArgumentException("Illegal mount client name: " + clientname);
			}
			DaemonConnectionIDEProperty connectionprop = getDaemonConnectionPropertyForConnectionName(connections,
					clientname);
			if (connectionprop == null) {
				throw new IllegalArgumentException("Mount daemon connection not found for client name: " + clientname);
			}
			result.add(connectionprop);
		}
		return result;
	}

	private SimpleScriptModellingEnvironmentConfiguration createScriptEnvironmentConfiguration(
			IDEProjectProperties properties) throws Exception {
		//TODO do creating the path configuration asynchronously, as connection can take time

		Collection<DaemonConnectionIDEProperty> connprops = getDaemonConnectionPropertiesForPathConfiguration(
				properties);
		Map<String, RemoteDaemonConnection> daemonconnections = plugin.connectToDaemonsFromPluginEnvironment(connprops);
		ExecutionPathConfiguration pathconfig = tryCreatePathConfigurationForScripting(properties, daemonconnections);
		ExecutionScriptConfiguration scriptconfig = tryCreateScriptConfigurationForScripting(properties);
		Set<WildcardPath> excludepaths = createScriptingExcludePaths(properties, pathconfig);
		ExecutionRepositoryConfiguration repoconfig = tryCreateRepositoryConfigurationForScripting(properties);

		Collection<? extends RepositoryConfig> repositoryconfigs = repoconfig == null ? Collections.emptySet()
				: repoconfig.getRepositories();
		List<ExternalScriptInformationProvider> externalscriptinfoproviders = new ArrayList<>();
		Map<String, String> userparammap = SakerIDEPlugin.entrySetToMap(properties.getUserParameters());
		if (!ObjectUtils.isNullOrEmpty(repositoryconfigs)) {
			Map<String, LazyLoadedDelegatingExternalScriptInformationProvider> configinfoproviders = new HashMap<>();
			for (RepositoryConfig rconfig : repositoryconfigs) {
				LazyLoadedDelegatingExternalScriptInformationProvider lazyinfoprovider = new LazyLoadedDelegatingExternalScriptInformationProvider(
						this);
				configinfoproviders.put(rconfig.getRepositoryIdentifier(), lazyinfoprovider);
				externalscriptinfoproviders.add(lazyinfoprovider);
			}

			scriptingBuildRepositoryLoader.startLoading(() -> {
				SakerExecutionCache executioncache = this.scriptingEnvironmentExecutionCache.get();
				if (executioncache == null) {
					//can be if closed
					return Collections.emptyMap();
				}
				ClassLoaderResolverRegistry resolverregistry = new ClassLoaderResolverRegistry(
						plugin.getPluginEnvironment().getClassLoaderResolverRegistry());
				executioncache.set(pathconfig, repoconfig, scriptconfig, userparammap, LocalFileProvider.getInstance(),
						resolverregistry, false, null);
				return executioncache.getLoadedBuildRepositories();
			}, loadedbuildrepos -> {
				for (Entry<String, ? extends BuildRepository> entry : loadedbuildrepos.entrySet()) {
					BuildRepository brepo = entry.getValue();
					ExternalScriptInformationProvider infoprovider = brepo.getScriptInformationProvider();
					if (infoprovider != null) {
						String repoid = entry.getKey();
						LazyLoadedDelegatingExternalScriptInformationProvider lazyprovider = configinfoproviders
								.get(repoid);
						lazyprovider.setProvider(infoprovider);
					}
				}
			}, loadexc -> {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to load scripting build repository.", loadexc);
			});
		}

		SimpleScriptModellingEnvironmentConfiguration scriptenvconfig = new SimpleScriptModellingEnvironmentConfiguration(
				pathconfig, scriptconfig, excludepaths, externalscriptinfoproviders, userparammap);
		return scriptenvconfig;
	}

	private ExecutionRepositoryConfiguration tryCreateRepositoryConfigurationForScripting(
			IDEProjectProperties properties) {
		try {
			return createRepositoryConfiguration(properties);
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to create repository configuration.", e);
			return null;
		}
	}

	private ExecutionScriptConfiguration tryCreateScriptConfigurationForScripting(IDEProjectProperties properties) {
		try {
			return createScriptConfiguration(properties);
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to create scripting configuration.", e);
			return null;
		}
	}

	private ExecutionPathConfiguration tryCreatePathConfigurationForScripting(IDEProjectProperties properties,
			Map<String, RemoteDaemonConnection> daemonconnections) {
		try {
			String workingdirproperty = properties.getWorkingDirectory();
			SakerPath wdpath = null;
			if (!ObjectUtils.isNullOrEmpty(workingdirproperty)) {
				wdpath = SakerPath.valueOf(workingdirproperty);
				if (wdpath.isRelative()) {
					//working dir is relative. override with a custom one as relative paths are not allowed
					wdpath = null;
				}
			}
			ExecutionPathConfiguration.Builder pathconfigbuilder;
			if (wdpath == null) {
				pathconfigbuilder = ExecutionPathConfiguration.builder(SCRIPTING_PSEUDO_PATH);
				pathconfigbuilder.addRootProvider(SCRIPTING_PSEUDO_PATH.getRoot(),
						SCRIPTING_PSEUDO_EMPTY_FILE_PROVIDER);
			} else {
				pathconfigbuilder = ExecutionPathConfiguration.builder(wdpath);
			}
			Set<? extends ProviderMountIDEProperty> mounts = properties.getMounts();
			if (!ObjectUtils.isNullOrEmpty(mounts)) {
				for (ProviderMountIDEProperty mount : mounts) {
					String normalizedmountroot;
					{
						String mountroot = mount.getRoot();
						try {
							normalizedmountroot = SakerPath.normalizeRoot(mountroot);
						} catch (Exception e) {
							displayException(SakerLog.SEVERITY_ERROR, "Failed to normalize mounted root: " + mountroot,
									e);
							//can't deal with invalid roots
							continue;
						}
					}
					MountPathIDEProperty mountprop = mount.getMountPathProperty();
					ProviderHolderPathKey mountpathkey;
					try {
						mountpathkey = getMountPathPropertyPathKey(mountprop, daemonconnections);
					} catch (Exception e) {
						displayException(SakerLog.SEVERITY_ERROR, "Failed to mount path.", e);
						//failed to resolve the mount provider and path key, fall back to pseudo provider
						mountpathkey = SakerPathFiles.getPathKey(SCRIPTING_PSEUDO_EMPTY_FILE_PROVIDER,
								SCRIPTING_PSEUDO_PATH);
					}

					pathconfigbuilder.addRootProvider(normalizedmountroot, DirectoryMountFileProvider
							.create(mountpathkey.getFileProvider(), mountpathkey.getPath(), normalizedmountroot));
				}
			}
			return pathconfigbuilder.build();
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to create path configuration.", e);
			return null;
		}
	}

	private static Set<WildcardPath> createScriptingExcludePaths(IDEProjectProperties properties,
			ExecutionPathConfiguration pathconfig) {
		Set<WildcardPath> result = new HashSet<>();
		String builddir = properties.getBuildDirectory();
		if (!ObjectUtils.isNullOrEmpty(builddir)) {
			SakerPath buildpath = SakerPath.valueOf(builddir);
			if (pathconfig != null && buildpath.isRelative()) {
				buildpath = pathconfig.getWorkingDirectory().resolve(buildpath);
			}
			result.add(WildcardPath.valueOf(buildpath + "/**"));
		}
		String mirrordir = properties.getMirrorDirectory();
		if (!ObjectUtils.isNullOrEmpty(mirrordir)
				&& ObjectUtils.isNullOrEmpty(properties.getExecutionDaemonConnectionName())) {
			//only exclude the mirror path if the execution daemon is in-process
			try {
				SakerPath mirrorpath = SakerPath.valueOf(mirrordir);
				if (pathconfig != null && mirrorpath.isAbsolute()) {
					//the specified mirror path should be absolute
					try {
						SakerPath execmirrorpath = pathconfig.toExecutionPath(LocalFileProvider.toRealPath(mirrorpath));
						if (execmirrorpath != null) {
							result.add(WildcardPath.valueOf(execmirrorpath + "/**"));
						}
					} catch (InvalidPathException e) {
						// if the path is not a valid path on the local file system
					}
				}
			} catch (InvalidPathFormatException e) {
				//failed to parse the mirror path property
			}
		}
		Set<String> exclusionprop = properties.getScriptModellingExclusions();
		if (exclusionprop != null) {
			for (String excludewildcard : exclusionprop) {
				result.add(WildcardPath.valueOf(excludewildcard));
			}
		}
		return result;
	}

	private RemoteDaemonConnection getDaemonConnectionWithName(String name, IDEProjectProperties properties)
			throws IOException {
		Set<? extends DaemonConnectionIDEProperty> connections = properties.getConnections();
		DaemonConnectionIDEProperty connprop = getDaemonConnectionPropertyForConnectionName(connections, name);
		if (connprop != null) {
			String netaddress = connprop.getNetAddress();
			InetSocketAddress sockaddress = NetworkUtils.parseInetSocketAddress(netaddress,
					DaemonLaunchParameters.DEFAULT_PORT);
			return plugin.getPluginDaemonEnvironment().connectTo(sockaddress);
		}
		//XXX reify exception
		throw new IOException("Daemon not found for name: " + name);
	}

	/**
	 * @throws IOException
	 *             sneaky thrown by visitor
	 */
	private ExecutionScriptConfiguration createScriptConfiguration(IDEProjectProperties properties) throws IOException {
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigs = properties.getScriptConfigurations();
		ExecutionScriptConfiguration.Builder builder = ExecutionScriptConfiguration.builder();
		if (!ObjectUtils.isNullOrEmpty(scriptconfigs)) {
			for (ScriptConfigurationIDEProperty sc : scriptconfigs) {
				ClassPathLocationIDEProperty scclasspath = sc.getClassPathLocation();

				ScriptProviderLocation scriptproviderlocation = new ScriptProviderLocationResolverVisitor(properties,
						sc).visit(scclasspath);

				builder.addConfig(WildcardPath.valueOf(sc.getScriptsWildcard()), new ScriptOptionsConfig(
						SakerIDEPlugin.entrySetToMap(sc.getScriptOptions()), scriptproviderlocation));
			}
		}
		return builder.build();
	}

	/**
	 * @throws IOException
	 *             sneaky thrown by visitor
	 */
	private ExecutionRepositoryConfiguration createRepositoryConfiguration(IDEProjectProperties properties)
			throws IOException {
		ExecutionRepositoryConfiguration.Builder builder = ExecutionRepositoryConfiguration.builder();
		Set<? extends RepositoryIDEProperty> repositories = properties.getRepositories();
		if (!ObjectUtils.isNullOrEmpty(repositories)) {
			for (RepositoryIDEProperty repo : repositories) {
				ClassPathLocationIDEProperty repocploc = repo.getClassPathLocation();
				ClassPathServiceEnumeratorIDEProperty reposerviceenumerator = repo.getServiceEnumerator();
				ClassPathLocation cploc = new ClassPathLocationResolverVisitor(properties).visit(repocploc);
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceenumerator = reposerviceenumerator
						.accept(new SubClassingServiceEnumeratorResolverVisitor<>(
								SakerEnvironment.class.getClassLoader(), SakerRepositoryFactory.class), null);
				String repoid = repo.getRepositoryIdentifier();
				if (ObjectUtils.isNullOrEmpty(repoid)) {
					//nullize empty strings
					repoid = null;
				}
				builder.add(cploc, serviceenumerator, repoid);
			}
		}
		return builder.build();
	}

	private ExecutionPathConfiguration createPathConfiguration(IDEProjectProperties properties)
			throws IOException, InterruptedException {
		Collection<DaemonConnectionIDEProperty> connprops = getDaemonConnectionPropertiesForPathConfiguration(
				properties);
		Map<String, RemoteDaemonConnection> daemonconnections = plugin.connectToDaemonsFromPluginEnvironment(connprops);
		return createPathConfiguration(properties, daemonconnections);
	}

	private ProviderHolderPathKey getMountPathPropertyPathKey(MountPathIDEProperty mountprop,
			Map<String, RemoteDaemonConnection> daemonconnections) {
		if (mountprop == null) {
			throw new NullPointerException("Missing mount.");
		}
		String mountclient = mountprop.getMountClientName();
		if (ObjectUtils.isNullOrEmpty(mountclient)) {
			throw new IllegalArgumentException("Missing mount client name.");
		}
		SakerPath mountedpath = SakerPath.valueOf(mountprop.getMountPath());
		switch (mountclient) {
			case MOUNT_ENDPOINT_LOCAL_FILESYSTEM: {
				return LocalFileProvider.getInstance().getPathKey(mountedpath);
			}
			case MOUNT_ENDPOINT_PROJECT_RELATIVE: {
				return LocalFileProvider.getInstance()
						.getPathKey(SakerPath.valueOf(projectPath).resolve(mountedpath.replaceRoot(null)));
			}
			default: {
				RemoteDaemonConnection daemonconnection = daemonconnections.get(mountclient);
				if (daemonconnection == null) {
					throw new IllegalArgumentException("Daemon connection not found with name: " + mountclient);
				}
				SakerFileProvider fp = daemonconnection.getDaemonEnvironment().getFileProvider();
				return new SimpleProviderHolderPathKey(fp, mountedpath);
			}
		}
	}

	private ExecutionPathConfiguration createPathConfiguration(IDEProjectProperties properties,
			Map<String, RemoteDaemonConnection> daemonconnections) {
		ExecutionPathConfiguration.Builder pathconfigbuilder = ExecutionPathConfiguration
				.builder(SakerPath.valueOf(properties.getWorkingDirectory()));
		Set<? extends ProviderMountIDEProperty> mounts = properties.getMounts();
		if (!ObjectUtils.isNullOrEmpty(mounts)) {
			for (ProviderMountIDEProperty mount : mounts) {
				MountPathIDEProperty mountprop = mount.getMountPathProperty();
				ProviderHolderPathKey mountpathkey = getMountPathPropertyPathKey(mountprop, daemonconnections);

				String mountroot = mount.getRoot();
				pathconfigbuilder.addRootProvider(mountroot, DirectoryMountFileProvider
						.create(mountpathkey.getFileProvider(), mountpathkey.getPath(), mountroot));
			}
		}
		return pathconfigbuilder.build();
	}

	@Deprecated
	private DaemonEnvironment getNamedDaemonEnvironment(IDEProjectProperties projprops, String name)
			throws IOException {
		if (name == null) {
			return plugin.getPluginDaemonEnvironment();
		}
		return getDaemonConnectionWithName(name, projprops).getDaemonEnvironment();
	}

	private final class RepositoryLoadExceptionHandlingSakerExecutionCache extends SakerExecutionCache {
		private RepositoryLoadExceptionHandlingSakerExecutionCache(SakerEnvironmentImpl environment) {
			super(environment);
		}

		@Override
		protected SakerRepository loadRepositoryFromManager(RepositoryManager repositorymanager,
				ClassPathLocation repolocation,
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) {
			try {
				return super.loadRepositoryFromManager(repositorymanager, repolocation, enumerator);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to load repository for scripting environment.", e);
				return null;
			}
		}
	}

	private static final class SubClassingServiceEnumeratorResolverVisitor<T>
			implements ClassPathServiceEnumeratorIDEProperty.Visitor<ClassPathServiceEnumerator<T>, Void> {
		private ClassLoader classLoader;
		private Class<? extends T> asSubClass;

		public SubClassingServiceEnumeratorResolverVisitor(ClassLoader classLoader, Class<? extends T> asSubClass) {
			this.classLoader = classLoader;
			this.asSubClass = asSubClass;
		}

		@Override
		public ClassPathServiceEnumerator<T> visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
			try {
				return new ServiceLoaderClassPathServiceEnumerator<>(
						Class.forName(property.getServiceClass(), false, classLoader).asSubclass(asSubClass));
			} catch (ClassNotFoundException | ClassCastException e) {
				throw new IllegalArgumentException(e);
			}
		}

		@Override
		public ClassPathServiceEnumerator<T> visit(NamedClassClassPathServiceEnumeratorIDEProperty property,
				Void param) {
			return new NamedCheckingClassPathServiceEnumerator<>(property.getClassName(), asSubClass);
		}

		@SuppressWarnings("unchecked")
		@Override
		public ClassPathServiceEnumerator<T> visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property,
				Void param) {
			//cast check
			ScriptAccessProvider.class.asSubclass(asSubClass);
			return (ClassPathServiceEnumerator<T>) BuiltinScriptAccessorServiceEnumerator.getInstance();
		}

		@SuppressWarnings("unchecked")
		@Override
		public ClassPathServiceEnumerator<T> visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property,
				Void param) {
			SakerRepositoryFactory.class.asSubclass(asSubClass);
			return (ClassPathServiceEnumerator<T>) NestRepositoryFactoryClassPathServiceEnumerator.getInstance();
		}
	}

	private final class ScriptProviderLocationResolverVisitor
			implements ClassPathLocationIDEProperty.Visitor<ScriptProviderLocation, Void> {
		private IDEProjectProperties properties;
		private ScriptConfigurationIDEProperty scriptProperty;

		public ScriptProviderLocationResolverVisitor(IDEProjectProperties properties,
				ScriptConfigurationIDEProperty scriptProperty) {
			this.properties = properties;
			this.scriptProperty = scriptProperty;
		}

		//throws IOException because it's sneakily thrown
		public ScriptProviderLocation visit(ClassPathLocationIDEProperty property) throws IOException {
			return property.accept(this, null);
		}

		@Override
		public ScriptProviderLocation visit(JarClassPathLocationIDEProperty property, Void param) {
			ClassPathLocation cplocation = new ClassPathLocationResolverVisitor(properties).visit(property, param);
			return new ScriptProviderLocation(cplocation, getServiceEnumeratorFromProperty());
		}

		@Override
		public ScriptProviderLocation visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
			ClassPathLocation cplocation = new ClassPathLocationResolverVisitor(properties).visit(property, param);
			return new ScriptProviderLocation(cplocation, getServiceEnumeratorFromProperty());
		}

		@Override
		public ScriptProviderLocation visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
			ClassPathServiceEnumeratorIDEProperty serviceenumerator = scriptProperty.getServiceEnumerator();
			if (!BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE.equals(serviceenumerator)) {
				throw new IllegalArgumentException(
						"Invalid script service enumerator for builtin scripting language class path: "
								+ serviceenumerator);
			}
			return ExecutionScriptConfiguration.ScriptProviderLocation.getBuiltin();
		}

		@Override
		public ScriptProviderLocation visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
			throw new IllegalArgumentException("Nest repository classpath is invalid for script configuration.");
		}

		private ClassPathServiceEnumerator<? extends ScriptAccessProvider> getServiceEnumeratorFromProperty() {
			ClassPathServiceEnumerator<? extends ScriptAccessProvider> serviceenumerator = scriptProperty
					.getServiceEnumerator().accept(new SubClassingServiceEnumeratorResolverVisitor<>(
							SakerEnvironment.class.getClassLoader(), ScriptAccessProvider.class), null);
			return serviceenumerator;
		}
	}

	private final class ClassPathLocationResolverVisitor
			implements ClassPathLocationIDEProperty.Visitor<ClassPathLocation, Void> {

		private IDEProjectProperties properties;

		public ClassPathLocationResolverVisitor(IDEProjectProperties properties) {
			this.properties = properties;
		}

		//throws IOException because it's sneakily thrown
		public ClassPathLocation visit(ClassPathLocationIDEProperty property) throws IOException {
			return property.accept(this, null);
		}

		@Override
		public ClassPathLocation visit(JarClassPathLocationIDEProperty property, Void param) {
			try {
				SakerPath jarpath = SakerPath.valueOf(property.getJarPath());
				String clientname = property.getConnectionName();
				if (ObjectUtils.isNullOrEmpty(clientname)) {
					throw new IllegalArgumentException(
							"Invalid client name for JAR class path location: " + clientname);
				}
				SakerFileProvider fp;
				ProviderHolderPathKey pathkey;
				switch (clientname) {
					case MOUNT_ENDPOINT_PROJECT_RELATIVE: {
						pathkey = LocalFileProvider.getInstance()
								.getPathKey(getProjectPath().resolve(jarpath.toString()));
						break;
					}
					case MOUNT_ENDPOINT_LOCAL_FILESYSTEM: {
						pathkey = LocalFileProvider.getInstance().getPathKey(jarpath);
						break;
					}
					default: {
						fp = getNamedDaemonEnvironment(properties, clientname).getFileProvider();
						pathkey = SakerPathFiles.getPathKey(fp, jarpath);
						break;
					}
				}
				return new JarFileClassPathLocation(pathkey);
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
		}

		@Override
		public ClassPathLocation visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
			String urlstr = property.getUrl();
			try {
				return new HttpUrlJarFileClassPathLocation(new URL(urlstr));
			} catch (NullPointerException | IllegalArgumentException | MalformedURLException e) {
				throw new IllegalArgumentException("Invalid URL. (" + urlstr + ")", e);
			}
		}

		@Override
		public ClassPathLocation visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
			return ExecutionScriptConfiguration.ScriptProviderLocation.getBuiltin().getClassPathLocation();
		}

		@Override
		public ClassPathLocation visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
			String version = property.getVersion();
			if (version == null) {
				return NestRepositoryClassPathLocation.getInstance();
			}
			return NestRepositoryClassPathLocation.getInstance(version);
		}
	}

	private static class ValidatedProjectProperties {
		final IDEProjectProperties properties;
		final Set<PropertiesValidationErrorResult> errors;

		public ValidatedProjectProperties(IDEProjectProperties properties,
				Set<PropertiesValidationErrorResult> errors) {
			this.properties = properties;
			this.errors = errors;
		}

		public boolean isValid() {
			return ObjectUtils.isNullOrEmpty(errors);
		}
	}

}
