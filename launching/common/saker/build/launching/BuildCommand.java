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
package saker.build.launching;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.net.SocketFactory;

import saker.build.daemon.BuildExecutionInvoker;
import saker.build.daemon.DaemonEnvironment;
import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.EnvironmentBuildExecutionInvoker;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.LocalDaemonEnvironment.RunningDaemonConnectionInfo;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.daemon.files.DaemonPath;
import saker.build.exception.BuildExecutionFailedException;
import saker.build.exception.BuildTargetNotFoundException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.content.ContentDescriptorSupplier;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.DirectoryMountFileProvider;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.NamedCheckingClassPathServiceEnumerator;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.environment.BuildTaskExecutionResult.ResultKind;
import saker.build.runtime.environment.EnvironmentParameters;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation.ConnectionInformation;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.runtime.execution.SakerLog.ExceptionFormat;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.params.InvalidBuildConfigurationException;
import saker.build.runtime.params.NestRepositoryClassPathLocation;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.task.cluster.TaskInvoker;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.function.IOFunction;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.util.exc.ExceptionView;
import saker.build.util.java.JavaTools;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.MultiParameter;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.api.PositionalParameter;
import sipka.cmdline.runtime.ArgumentResolutionException;
import sipka.cmdline.runtime.InvalidArgumentFormatException;
import sipka.cmdline.runtime.InvalidArgumentValueException;
import sipka.cmdline.runtime.MissingArgumentException;
import sipka.cmdline.runtime.ParseUtil;
import sipka.cmdline.runtime.ParsingIterator;

/**
 * <pre>
 * Execute a build with the given parameters.
 * 
 * A build can be configured to run in this process, use a local daemon,
 * or use a remote daemon.
 * The build daemon is only used if either the -daemon flag or the -daemon-address
 * parameter is defined.
 * </pre>
 */
public class BuildCommand {
	public static final String DEFAULT_WORKING_DIRECTORY_ROOT = "wd:";
	public static final String DAEMON_CLIENT_NAME_LOCAL = "local";
	public static final String DAEMON_CLIENT_NAME_REMOTE = "remote";
	public static final String DAEMON_CLIENT_NAME_PROC_WORK_DIR = "pwd";

	private static final SakerPath DEFAULT_WORKING_DIRECTORY_PATH = SakerPath.valueOf(DEFAULT_WORKING_DIRECTORY_ROOT);
	private static final Set<String> RESERVED_CONNECTION_NAMES = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { DAEMON_CLIENT_NAME_LOCAL, DAEMON_CLIENT_NAME_REMOTE,
					DAEMON_CLIENT_NAME_PROC_WORK_DIR, "build", "http", "https", "ftp", "sftp", "file", "data", "jar",
					"zip", "url", "uri", "tcp", "udp", "mem", "memory", "ide", "project", "null", "storage", "nest" });

	private static final String PARAM_NAME_DAEMON_ADDRESS = "-daemon-address";
	private static final String PARAM_NAME_U = "-U";
	private static final String PARAM_NAME_P = "-P";
	private static final String PARAM_NAME_DBCONFIG_PATH = "-dbconfig-path";
	private static final String PARAM_NAME_TRACE = "-trace";
	private static final String PARAM_NAME_MOUNT = "-mount";
	private static final String PARAM_NAME_CONNECT = "-connect";
	private static final String PARAM_NAME_CLUSTER = "-cluster";
	private static final String PARAM_NAME_CLUSTER_USE_CLIENTS = "-cluster-use-clients";

	private static final String PARAM_NAME_REPOSITORY_CLASS = "-repository-class";
	private static final String PARAM_NAME_REPOSITORY_ID = "-repository-id";
	private static final String PARAM_NAME_REPOSITORY = "-repository";

	private static final String PARAM_NAME_SCRIPT_CLASSPATH = "-script-classpath";
	private static final String PARAM_NAME_SCRIPT_CLASS = "-script-class";
	private static final String PARAM_NAME_SO = "-SO";
	private static final String PARAM_NAME_SCRIPT_FILES = "-script-files";

	private static final String DEFAULT_BUILD_FILE_NAME = "saker.build";

	private static final Map<BuildTaskExecutionResult.ResultKind, String> BUILD_RESULT_KIND_INFO_MAP = new EnumMap<>(
			BuildTaskExecutionResult.ResultKind.class);
	static {
		BUILD_RESULT_KIND_INFO_MAP.put(ResultKind.SUCCESSFUL, "Build successful");
		BUILD_RESULT_KIND_INFO_MAP.put(ResultKind.FAILURE, "Build failed");
		BUILD_RESULT_KIND_INFO_MAP.put(ResultKind.INITIALIZATION_ERROR, "Build initialization error");
	}

	/**
	 * <pre>
	 * Specifies the working directory for the build execution.
	 * 
	 * The specified path will be resolved based on the build path configuration.
	 * This means that absolute paths will be based against 
	 * the root directories for the execution, not against the current file system 
	 * that is executing the process.
	 * </pre>
	 */
	@Parameter({ "-working-directory", "-working-dir", "-wd" })
	public SakerPath workingDirectory;
	/**
	 * <pre>
	 * Specifies the build directory for the build execution.
	 * 
	 * In general, most of the outputs of the build will be placed in the
	 * specified build directory.
	 * The given path can be absolute or relative. Relative paths will be
	 * resolved against the specified working directory. Absolute paths 
	 * are resolved against the build path configuration. (Similar to 
	 * the working directory.)
	 * </pre>
	 */
	@Parameter({ "-build-directory", "-build-dir", "-bd" })
	public SakerPath buildDirectory;
	/**
	 * <pre>
	 * Specifies the mirror directory for the build execution.
	 * 
	 * The mirror directory is a path on the local file system, which is used when
	 * files need to be present on the file system. This can be the case when some tasks
	 * need to invoke external processes to execute their work.
	 * 
	 * If the mirror directory is not specified, the build system will try to use a 
	 * directory under the build directory. If the build directory doesn't reside
	 * on the local file system, no mirror directory will be used by default.
	 * 
	 * The specified path must be absolute and will be resolved against 
	 * the local file system (that is the file system of the build machine).
	 * (Unlike the working directory.)
	 * </pre>
	 */
	@Parameter({ "-mirror-directory", "-mirror-dir", "-md" })
	public SakerPath mirrorDirectory;

	/**
	 * <pre>
	 * Mounts a given directory for the build execution.
	 * 
	 * The mounted directory will be added to the build execution, and it will be
	 * available for the tasks as a root directory for files.
	 * 
	 * The arguments for this parameter is in the following format:
	 * 
	 *     name://absolute/path/to/directory mounted:
	 *     
	 * In which case the the "/absolute/path/to/directory" on the connection "name"
	 * will be mounted as the root directory "mounted:".
	 * 
	 * For Windows file system paths the path can be specified as:
	 * 
	 *     name:/c:/absolute/path
	 *     
	 * Where "name" is the connection name and "c:" is the drive letter for the
	 * absolute path.
	 * 
	 * The "name:/" part can be omitted, in which case the remaining part will be 
	 * interpreted for the local file system.
	 * 
	 * If the name "local" is specified, the remaining absolute path is interpreted
	 * for the local file system.
	 * If the name "remote" is specified, the remaining absolute path is interpreted
	 * for the file system of the daemon (if any) that is running the build.
	 * If the name "pwd" is specified, the remaining absolute path names are 
	 * interpreted against the process working directory.
	 * 
	 * Can be used multiple times to mount multiple directories.
	 * </pre>
	 */
	@Parameter({ PARAM_NAME_MOUNT })
	@MultiParameter(DirectoryMountParam.class)
	public Collection<DirectoryMountParam> mount = new LinkedHashSet<>();

	private NavigableMap<String, String> userParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Specifes user parameters for the build execution.
	 * 
	 * User parameters are arbitrary key-value pairs which can be used to dynamically
	 * configure different aspects of the build system. This is usually applicable for
	 * configuring repositories or specific tasks you're executing.
	 * 
	 * Can be used multiple times to define multiple entries.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_U)
	public void userParameter(String key, String value) {
		if (userParameters.containsKey(key)) {
			throw new InvalidArgumentValueException("User parameter specified multiple times: " + key, PARAM_NAME_U);
		}
		userParameters.put(key, value);
	}

	private NavigableMap<String, String> buildTargetParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Sets a build target parameter.
	 * 
	 * Sets the value of a build target parameter that is directly passed to the 
	 * invoked build target.
	 * 
	 * Can be used multiple times to set multiple different parameters.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_P)
	public void buildTargetParameter(String key, String value) {
		if (buildTargetParameters.containsKey(key)) {
			throw new InvalidArgumentValueException("User parameter specified multiple times: " + key, PARAM_NAME_U);
		}
		buildTargetParameters.put(key, value);
	}

	@ParameterContext
	public RepositoryCollector repositoryCollector = new RepositoryCollector();

	@ParameterContext
	public ScriptConfigCollector scriptConfigCollector = new ScriptConfigCollector();

	@ParameterContext
	public DatabaseConfigurationCollection databaseConfigCollector = new DatabaseConfigurationCollection();

	/**
	 * <pre>
	 * Specifies a daemon connection that should be used as a cluster for the build.
	 * 
	 * This parameter adds connection specified by the given name as cluster to
	 * the build execution. Clusters can be used to execute a build over multiple
	 * computers by delegating tasks to it.
	 * 
	 * Note, that only those tasks can be delegated to clusters which actually
	 * support this feature.
	 * 
	 * Connections can be specified using the -connect parameter.
	 * 
	 * This parameter can be specified multiple times.
	 * Specifying a connection multiple times as a cluster has no additional
	 * effects.
	 * </pre>
	 * 
	 * @cmd-format &lt;connection-name>
	 */
	@Parameter({ PARAM_NAME_CLUSTER })
	@MultiParameter(String.class)
	public Set<String> clusters = new LinkedHashSet<>();

	/**
	 * <pre>
	 * Flag to specify to use the connected clients of the used daemon as clusters.
	 * 
	 * If the daemon that is used for build execution has other clients connected
	 * to it, then they will be used as clusters for this build execution.
	 * 
	 * The default is false.
	 * </pre>
	 */
	@Flag
	@Parameter(PARAM_NAME_CLUSTER_USE_CLIENTS)
	public boolean clusterUseClients = false;

	/**
	 * <pre>
	 * Specifies a connection to a given address and identifies it by the specified name.
	 * 
	 * A connection will be established to the build system daemon 
	 * running at the given address. The connection can be used to add 
	 * file systems to the build, run the build itself on that daemon, or others.
	 * 
	 * The established connection can be referenced by its name in other parameters.
	 * The names for the connections must be unique, only one connection 
	 * can be specified for a given name.
	 * 
	 * This parameter can be specified multiple times.
	 * 
	 * Connection names which represent well known URL protocols or identifiers,
	 * may be reserved and an exception will be thrown. Such names are:
	 * 
	 *     local, remote, http, https, file, jar, ftp and others...
	 *       (The list of names may change incompatibly in the future.)
	 * 
	 * If you encounter incompatibility, simply choose a different name for your
	 * connection.
	 * 
	 * The default port for the addresses is 3500.
	 * </pre>
	 */
	@Parameter({ PARAM_NAME_CONNECT })
	@MultiParameter(DaemonConnectParam.class)
	public Collection<DaemonConnectParam> connect = new LinkedHashSet<>();

	/**
	 * <pre>
	 * Flag for specifying if a daemon should be used for build execution.
	 * 
	 * If this flag is specified, a daemon on the local machine will
	 * be used for the execution of the build. If the daemon is not 
	 * running, it will be started with the properties specified by
	 * other -daemon parameters.
	 * 
	 * If a daemon is already running, then that will be used if the configuration
	 * of it is acceptable. If there is a configuration mismatch between
	 * the expected and actual configurations, an exception will be thrown.
	 * 
	 * If -daemon-address is also specified, this flag specifies that
	 * a daemon process on the local machine can be started if necessary.
	 * The -daemon parameters will be used when starting the new daemon.
	 * </pre>
	 */
	@Flag
	@Parameter
	public boolean daemon;

	/**
	 * <pre>
	 * Specifies the network address of the daemon that should be used for build execution.
	 * 
	 * The default port for the address is 3500.
	 * 
	 * If this parameter is specified, a local daemon will only be instantiated
	 * if required and the -daemon flag is specified.
	 * 
	 * Specifying a loopback or local address is the same as specifying the 
	 * -daemon flag.
	 * 
	 * See -daemon parameter flag.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_DAEMON_ADDRESS)
	public DaemonAddressParam daemonAddress;

	@ParameterContext
	public StorageDirectoryParamContext storageDirectoryParam = new StorageDirectoryParamContext();

	@ParameterContext
	public EnvironmentParamContext envParams = new EnvironmentParamContext();

	@ParameterContext
	public SakerJarLocatorParamContext sakerJarLocator = new SakerJarLocatorParamContext();

	/**
	 * <pre>
	 * Flag for specifying that the build execution should not use any caching
	 * for keeping the project-related data in memory.
	 * 
	 * When this flag is not used and the build is executed via a build daemon, some
	 * project related data will be cached for some amount of time for faster
	 * incremental builds.
	 * Specifying this flag will turn that off and the build will not use
	 * cached information for its execution.
	 * </pre>
	 */
	@Flag(negate = true)
	@Parameter("-no-daemon-cache")
	public boolean useDaemonCache = true;

	/**
	 * <pre>
	 * Specifies the format of the stack trace that is printed in case of build failure.
	 * 
	 * Possible values are:
	 * 
	 * - full
	 *   - Every stack trace element and script trace will be printed.
	 * - reduced
	 *   - Some internal stack traces will be removed, but the number of removed
	 *       frames will be included in the stack trace.
	 * - compact
	 *   - Interal stack traces are removed, and no indicator of such removal
	 *       is displayed on the printed information.
	 * - script_only
	 *   - Only script traces are displayed for the exceptions.
	 *       No Java stack traces are displayed.
	 * - java_trace
	 *   - Only Java stack traces are displayed for the exceptions.
	 *       No build script traces are displayed.
	 * </pre>
	 */
	@Parameter("-stacktrace")
	public CommonExceptionFormat stackTraceFormat;

	/**
	 * <pre>
	 * Flag for specifying to run in interactive mode.
	 * 
	 * Interactive mode entails that the build tasks may read input
	 * from the console. It usually means that a developer actively
	 * monitors the build.
	 * 
	 * This can be used for local builds and is not recommended
	 * for builds running on a Continuous Integration server or where
	 * the developer has no opportunity to provide manual input.
	 * </pre>
	 */
	@Flag
	@Parameter("-interactive")
	public boolean interactive = false;

	/**
	 * <pre>
	 * Sets the output path of the build trace for the build execution.
	 * 
	 * The path is expected to be in the same format as in the -mount 
	 * parameter.
	 * 
	 * The build trace can be viewed in a browser, by navigating to:
	 *     https://saker.build/buildtrace
	 * and opening it on the page.
	 * (The build trace can be viewed offline, it won't be transferred 
	 * to our servers.)
	 * </pre>
	 * 
	 * @cmd-format &lt;mount-path>
	 */
	@Parameter(PARAM_NAME_TRACE)
	public String buildTracePath;

	/**
	 * <pre>
	 * Instructs the build trace to embed the output build artifacts
	 * in the created build trace file.
	 * 
	 * If this flag is specified, the build artifacts that the 
	 * build tasks produce will be embedded in the build trace. They
	 * can be downloaded directly from the build trace view.
	 * </pre>
	 */
	@Parameter("-trace-artifacts-embed")
	@Flag
	public boolean buildTraceEmbedArtifacts;

	/**
	 * <pre>
	 * The build target to execute.
	 * 
	 * If not specified then defaults to the following:
	 * 
	 * - If there is only one target in the build file then that one is invoked.
	 * - The target with the name "build" is invoked if exists.
	 * - An exception is thrown otherwise.
	 * </pre>
	 */
	@Parameter
	@PositionalParameter(-1)
	public String target;

	/**
	 * <pre>
	 * The build script file to invoke the target of.
	 * 
	 * Defaults to the script with the name "saker.build" in the working directory.
	 * If that one doesn't exist, then a build script is selected only if there is only
	 * one build script in the working directory.
	 * An exception is thrown if a build script file is not found.
	 * </pre>
	 */
	@Parameter("build-script")
	@PositionalParameter(-2)
	public SakerPath buildScriptFile;

	@ParameterContext
	public AuthKeystoreParamContext authParams = new AuthKeystoreParamContext();

	private PrintStream stdOut;
	private PrintStream stdErr;
	private InputStream stdIn;

	private SakerPath userDir;

	public SakerPath getUserDir() {
		SakerPath result = userDir;
		if (result == null) {
			result = SakerPath.valueOf(System.getProperty("user.dir"));
			userDir = result;
		}
		return result;
	}

	public void init() {
		stdOut = System.out;
		stdErr = System.err;
		stdIn = System.in;
	}

	public void call(MainCommand mc) throws Exception {
		//build command should be finished with a system.exit if called from command line
		mc.shouldSystemExit = true;
		if (this.daemon || daemonAddress != null) {
			if (daemonAddress != null) {
				//there is an address specified, only create a local daemon if necessary, and -daemon is specified

				InetSocketAddress remoteaddress = daemonAddress.getSocketAddressThrowArgumentException();
				if (this.daemon) {
					//we're allowed to create a local daemon if necessary
					DaemonLaunchParameters launchparams = getLaunchDaemonParameters();
					if (LaunchingUtils.isLocalAddress(remoteaddress.getAddress())) {
						//we're connecting to a local daemon
						try (RemoteDaemonConnection connection = connectOrCreateDaemon(
								JavaTools.getCurrentJavaExePath(), sakerJarLocator.getSakerJarPath(), launchparams,
								authParams)) {
							verifyDaemonConfiguration(launchparams, connection);

							DaemonEnvironment daemonenv = connection.getDaemonEnvironment();
							runBuild(daemonenv, null);
						}
					} else {
						//we're using a non-local daemon to build
						if (useDaemonCache) {
							//we want to use caching for the project, so we need a persistent connection to the build daemon
							//this is in order so the file watching and stuff works properly
							//connect or create a daemon and connect via that
							//it is required for the local daemon to act as a client
							try (RemoteDaemonConnection localconnection = connectOrCreateDaemon(
									JavaTools.getCurrentJavaExePath(), sakerJarLocator.getSakerJarPath(), launchparams,
									authParams)) {
								verifyDaemonConfiguration(launchparams, localconnection);

								DaemonEnvironment env = localconnection.getDaemonEnvironment();
								try (RemoteDaemonConnection remoteconnection = env.connectTo(remoteaddress)) {
									runBuild(remoteconnection.getDaemonEnvironment(),
											localconnection.getDaemonEnvironment());
								}
							}
						} else {
							//we don't use caching, so we can just directly connect to the daemon
							try (RemoteDaemonConnection connection = RemoteDaemonConnection
									.connect(authParams.getSocketFactory(), remoteaddress)) {
								//no need to verify the daemon connection, as environment related configuration is related to local daemons
								runBuild(connection.getDaemonEnvironment(), null);
							}
						}
					}
				} else {
					//we're not allowed to start a new daemon
					//just directly connect to the daemon address
					try (RemoteDaemonConnection connection = RemoteDaemonConnection.connect(remoteaddress)) {
						//no need to verify the daemon connection, as environment related configuration is related to local daemons
						runBuild(connection.getDaemonEnvironment(), null);
					}
				}

//				//TODO test if the project watching and stuff works on network building
//				//if there is no cache used (no project), then we don't have to use a local proxy daemon, 
//				//    as the connection doesn't have to persist after the build is over
//				if (!useCache || isLocalAddress(remoteaddress.getAddress())) {
//					//doesnt matter which daemon we connect, we can always build directly
//					try (RemoteDaemonConnection connection = RemoteDaemonConnection.connect(remoteaddress)) {
//						runBuild(connection.getDaemonEnvironment(), null);
//					}
//				} else {
//					//we are connecting to a non local daemon, and we should cache the project,
//					//if we're connecting to a remote daemon, we have to use a local daemon to keep the caching alive
//					//if we dont use it, the the connection will be broken after the build and the project caching could call
//					//    methods for the local file provider, which will result in an error
//					//so: if we're building on a remote daemon, execute the build through the local daemon
//
//					//we should build using the local daemon
//					DaemonLaunchParameters localdaemonparams = instantiateDaemonParams.toLaunchParameters();
//					//it is required for the local daemon to act as a client
//					try (RemoteDaemonConnection localconnection = connectOrCreateDaemon(JavaTools.getCurrentJavaExePath(),
//							sakerJarLocator.getSakerJarPath(), localdaemonparams)) {
//						DaemonEnvironment env = localconnection.getDaemonEnvironment();
//						RemoteDaemonConnection remoteconnection = env.connectTo(remoteaddress);
//						runBuild(remoteconnection.getDaemonEnvironment(), localconnection.getDaemonEnvironment());
//						//we dont close the remote env, as the connection needs to stay after the build finishes
//					}
//				}
			} else {
				//no address specified.
				//connect or create on localhost with given parameters
				//attempt to choose a running daemon that matches the requested configuration or create

				DaemonLaunchParameters launchparams = getLaunchDaemonParameters();
				List<RunningDaemonConnectionInfo> runninginfos = LocalDaemonEnvironment
						.getRunningDaemonInfos(launchparams.getStorageDirectory());
				//auth params are passed to the daemon as well.
				//this is needed in multi-user systems so they don't interfere with our daemon
				try (RemoteDaemonConnection connection = findRunningDaemonWithParamsOrCreate(runninginfos, launchparams,
						JavaTools.getCurrentJavaExePath(), sakerJarLocator.getSakerJarPath(), authParams)) {
					runBuild(null, connection.getDaemonEnvironment());
				}
			}
		} else {
			// not using daemon
			EnvironmentParameters modenvparams = EnvironmentParameters.builder(sakerJarLocator.getSakerJarPath())
					.setUserParameters(envParams.getEnvironmentUserParameters())
					.setThreadFactor(envParams.getThreadFactor())
					.setStorageDirectory(storageDirectoryParam.getStorageDirectoryPath()).build();
			try (SakerEnvironmentImpl sakerenv = new SakerEnvironmentImpl(modenvparams)) {
				sakerenv.redirectStandardIO();
				runBuild(null, null, new EnvironmentBuildExecutionInvoker(sakerenv));
			}
		}
	}

	private static RemoteDaemonConnection findRunningDaemonWithParamsOrCreate(
			Iterable<? extends RunningDaemonConnectionInfo> daemons, DaemonLaunchParameters params, Path javaexe,
			Path sakerjarpath, AuthKeystoreParamContext authparams) throws IOException {
		InetAddress loopbackaddress = InetAddress.getLoopbackAddress();
		SakerPath authkspath = authparams.getAuthKeystorePath();
		if (authkspath != null) {
			//only attempt daemons that use secure connections
			List<RunningDaemonConnectionInfo> secureddaemons = new ArrayList<>();
			for (RunningDaemonConnectionInfo conninfo : daemons) {
				String kspathstr = conninfo.getSslKeystorePath();
				if (ObjectUtils.isNullOrEmpty(kspathstr)) {
					continue;
				}
				secureddaemons.add(conninfo);
			}
			daemons = secureddaemons;
		}
		//XXX parallelize?
		Exception[] connfails = {};
		for (RunningDaemonConnectionInfo conninfo : daemons) {
			InetSocketAddress address = new InetSocketAddress(loopbackaddress, conninfo.getPort());
			String connkspathstr = conninfo.getSslKeystorePath();
			SocketFactory socketfactory;
			if (ObjectUtils.isNullOrEmpty(connkspathstr)) {
				//use default, not secure connection
				socketfactory = null;
			} else {
				socketfactory = authparams.getSocketFactoryForDefaultedKeystore(SakerPath.valueOf(connkspathstr));
			}
			try {
				RemoteDaemonConnection connection = RemoteDaemonConnection.connect(socketfactory, address);
				try {
					verifyDaemonConfiguration(params, connection);
					return connection;
				} catch (Throwable e) {
					//if verification fails due to different configuration, close the connection and attempt the next one if any
					IOUtils.addExc(e, IOUtils.closeExc(connection));
					throw e;
				}
			} catch (IOException | InvalidBuildConfigurationException e) {
				connfails = ArrayUtils.appended(connfails, e);
			}
		}
		SocketFactory socketfactory = authparams.getSocketFactory();
		try {
			RemoteDaemonConnection connection = StartDaemonCommand.createDaemon(javaexe, sakerjarpath, params,
					authparams, null, socketfactory);
			//verify the newly created daemon nonetheless
			try {
				verifyDaemonConfiguration(params, connection);
				return connection;
			} catch (Throwable e) {
				//if verification fails due to different configuration, close the connection
				IOUtils.addExc(e, IOUtils.closeExc(connection));
				throw e;
			}
		} catch (IOException | InvalidBuildConfigurationException e) {
			for (Throwable supp : connfails) {
				e.addSuppressed(supp);
			}
			throw e;
		}
	}

	private DaemonLaunchParameters getLaunchDaemonParameters() {
		DaemonLaunchParameters.Builder builder = DaemonLaunchParameters.builder();
		builder.setPort(DaemonLaunchParameters.DEFAULT_PORT);
		builder.setStorageDirectory(storageDirectoryParam.getStorageDirectory());
		envParams.applyToBuilder(builder);
		return builder.build();
	}

	private ExceptionFormat getStackTraceFormat() {
		CommonExceptionFormat result = stackTraceFormat;
		if (result == null) {
			return CommonExceptionFormat.DEFAULT_FORMAT;
		}
		return result;
	}

	private SakerPath getWorkingDirectory() {
		SakerPath result = workingDirectory;
		if (result == null) {
			return getUserDir();
		}
		if (result.isRelative()) {
			return getUserDir().resolve(result);
		}
		return result;
	}

	private BuildTaskExecutionResult runBuild(DaemonEnvironment remoteenv, DaemonEnvironment localenv,
			BuildExecutionInvoker envcontroller) throws Exception {
		try (ResourceCloser rescloser = new ResourceCloser()) {
			DaemonEnvironment builddaemonenv = ObjectUtils.nullDefault(remoteenv, localenv);
			NavigableMap<String, RemoteDaemonConnection> connections;
			BuildInformation buildinfo = null;
			if (!connect.isEmpty()) {
				NavigableMap<String, ConnectionInformation> machinesinfo = new ConcurrentSkipListMap<>();
				buildinfo = new BuildInformation();
				connections = new ConcurrentSkipListMap<>();
				IOFunction<DaemonConnectParam, RemoteDaemonConnection> connector;

				if (builddaemonenv == null) {
					connector = c -> {
						InetSocketAddress address = c.address.getSocketAddressThrowArgumentException();
						SocketFactory socketfactory = authParams.getSocketFactoryForDaemonConnection(address);
						return RemoteDaemonConnection.connect(socketfactory, address);
					};
				} else {
					connector = c -> builddaemonenv.connectTo(c.address.getSocketAddressThrowArgumentException());
				}
				ThreadUtils.runParallelItems(connect, c -> {
					if (RESERVED_CONNECTION_NAMES.contains(c.name)) {
						throw new InvalidArgumentValueException(c.name + " is a reserved connection name.",
								PARAM_NAME_CONNECT);
					}
					RemoteDaemonConnection connection = connector.apply(c);
					rescloser.add(connection);
					RemoteDaemonConnection prevc = connections.put(c.name, connection);
					if (prevc != null) {
						throw new InvalidArgumentValueException("Multiple connections specified with name: " + c.name,
								PARAM_NAME_CONNECT);
					}
					//XXX the following probably implies too many RMI requests, which can delay the start of the build
					ConnectionInformation conninfo = new ConnectionInformation();
					conninfo.setConnectionRootFileProviderUUID(SakerPathFiles
							.getRootFileProviderKey(connection.getDaemonEnvironment().getFileProvider()).getUUID());
					conninfo.setConnectionBuildEnvironmentUUID(
							connection.getDaemonEnvironment().getEnvironmentIdentifier());
					conninfo.setConnectionAddress(c.address.getArgumentString());
					machinesinfo.put(c.name, conninfo);
				});
				buildinfo.setConnectionInformations(machinesinfo);
			} else {
				connections = Collections.emptyNavigableMap();
			}

			SakerPath workingdir = getWorkingDirectory();

			ExecutionParametersImpl params = createExecutionParameters(envcontroller, remoteenv, localenv, workingdir,
					connections);
			params.setBuildInfo(buildinfo);

			ExecutionPathConfiguration pathconfiguration = params.getPathConfiguration();
			workingdir = pathconfiguration.getWorkingDirectory();

			ProjectCacheHandle project = null;
			if (builddaemonenv != null && useDaemonCache) {
				project = builddaemonenv.getProject(pathconfiguration.getPathKey(workingdir));
			}

			if (this.buildScriptFile == null) {
				NavigableSet<SakerPath> buildfiles = getBuildFileNamesInDirectory(workingdir,
						params.getScriptConfiguration(), pathconfiguration.getFileProvider(workingdir));
				SakerPath defaultbuildfilepath = workingdir.resolve(DEFAULT_BUILD_FILE_NAME);
				if (buildfiles.contains(defaultbuildfilepath)) {
					this.buildScriptFile = defaultbuildfilepath;
				} else {
					if (buildfiles.size() != 1) {
						if (buildfiles.isEmpty()) {
							throw new BuildTargetNotFoundException("No build files found.");
						}
						throw new BuildTargetNotFoundException(
								"Failed to determine build file to use: " + StringUtils.toStringJoin(", ", buildfiles));
					}
					this.buildScriptFile = buildfiles.first();
				}
			}
			long nanos = System.nanoTime();
			BuildTaskExecutionResult execres = envcontroller.runBuildTargetWithLiteralParameters(this.buildScriptFile,
					this.target, params, project, buildTargetParameters);
			long endnanos = System.nanoTime();

			ResultKind executionresultkind = execres.getResultKind();
			String stateinfo = BUILD_RESULT_KIND_INFO_MAP.getOrDefault(executionresultkind, "Build finished");
			System.out.println(stateinfo + ". (" + DateUtils.durationToString((endnanos - nanos) / 1_000_000) + ")");

			ExceptionView excview = execres.getPositionedExceptionView();
			if (excview != null) {
				//XXX option for not omitting transitives?
				TaskUtils.printTaskExceptionsOmitTransitive(excview, System.err, workingdir, getStackTraceFormat());
				//do not retrieve the real exception, as that may not be RMI transferrable.
				String msg = excview.getMessage();
				if (executionresultkind != ResultKind.SUCCESSFUL) {
					throw new BuildExecutionFailedException(
							excview.getExceptionClassName() + (ObjectUtils.isNullOrEmpty(msg) ? "" : ": " + msg));
				}
			}

			return execres;
		}
	}

	private BuildTaskExecutionResult runBuild(DaemonEnvironment remoteenv, DaemonEnvironment localenv)
			throws Exception {
		BuildExecutionInvoker envcontroller;
		if (remoteenv != null) {
			envcontroller = remoteenv.getExecutionInvoker();
		} else if (localenv != null) {
			envcontroller = localenv.getExecutionInvoker();
		} else {
			throw new AssertionError("Internal consistency error: no remote and no local daemon for build.");
		}
		return runBuild(remoteenv, localenv, envcontroller);
	}

	private ExecutionParametersImpl createExecutionParameters(BuildExecutionInvoker envcontroller,
			DaemonEnvironment remoteenv, DaemonEnvironment localenv, SakerPath workingdir,
			NavigableMap<String, ? extends RemoteDaemonConnection> connections) throws IOException {
		ExecutionParametersImpl params = new ExecutionParametersImpl();
		if (buildDirectory != null) {
			params.setBuildDirectory(buildDirectory);
		}
		if (mirrorDirectory != null) {
			params.setMirrorDirectory(mirrorDirectory);
		}
		if (buildTracePath != null) {
			ProviderHolderPathKey buildtraceoutpathkey = mountPathToPathKey(PARAM_NAME_TRACE,
					DaemonPath.valueOf(buildTracePath), remoteenv, localenv, connections);
			params.setBuildTraceOutputPathKey(buildtraceoutpathkey);
			params.setBuildTraceEmbedArtifacts(buildTraceEmbedArtifacts);
		}
		params.setUserParameters(userParameters);
		params.setIO(ByteSink.valueOf(stdOut), ByteSink.valueOf(stdErr),
				!interactive ? StreamUtils.nullByteSource() : ByteSource.valueOf(stdIn));
		if (interactive) {
			Console console = System.console();
			if (console != null) {
				params.setSecretInputReader(new ConsoleSecretInputReader(console));
			}
		}
		ExecutionPathConfiguration pathconfiguration = createPathConfiguration(remoteenv, localenv, workingdir,
				connections);
		params.setPathConfiguration(pathconfiguration);

		DatabaseConfiguration databaseconfiguration;
		if (databaseConfigCollector.isEmpty()) {
			databaseconfiguration = DatabaseConfiguration.getDefault();
		} else {
			DatabaseConfiguration.Builder builder = DatabaseConfiguration
					.builder(databaseConfigCollector.getFallbackContentDescriptorSupplier());
			for (DatabaseConfigParam confparam : databaseConfigCollector.getConfigParams()) {
				builder.add(getRootFileProvideKeyOfConnection(PARAM_NAME_DBCONFIG_PATH, confparam.connectionName,
						remoteenv, localenv, connections), confparam.wildcard, confparam.descriptorSupplier);
			}
			databaseconfiguration = builder.build();
		}
		params.setDatabaseConfiguration(databaseconfiguration);

		if (scriptConfigCollector.isEmpty()) {
			params.setScriptConfiguration(ExecutionScriptConfiguration.getDefault());
		} else {
			ExecutionScriptConfiguration.Builder scbuilder = ExecutionScriptConfiguration.builder();
			for (ScriptConfigCollector.ConfigEntry scentry : scriptConfigCollector.getConfigs()) {
				ScriptProviderLocation scproviderlocation;
				if (scentry.classPath == null) {
					if (scentry.serviceEnumerator != null) {
						throw new IllegalArgumentException(
								"Cannot specify script language provider class name without classpath.");
					}
					scproviderlocation = ScriptProviderLocation.getBuiltin();
				} else {
					ClassPathServiceEnumerator<? extends ScriptAccessProvider> scriptserviceenumerator = scentry.serviceEnumerator;
					if (scriptserviceenumerator == null) {
						scriptserviceenumerator = new ServiceLoaderClassPathServiceEnumerator<>(
								ScriptAccessProvider.class);
					}
					ClassPathLocation scriptclasspath = createClassPathLocation(PARAM_NAME_SCRIPT_CLASSPATH,
							scentry.classPath, remoteenv, localenv, connections, pathconfiguration, false);
					scproviderlocation = new ScriptProviderLocation(scriptclasspath, scriptserviceenumerator);
				}
				ScriptOptionsConfig scoptionsconfig = new ScriptOptionsConfig(scentry.options, scproviderlocation);
				scbuilder.addConfig(scentry.files, scoptionsconfig);
			}
			params.setScriptConfiguration(scbuilder.build());
		}
		if (repositoryCollector.isEmpty()) {
			if (repositoryCollector.noNestRepository) {
				params.setRepositoryConfiguration(ExecutionRepositoryConfiguration.empty());
			} else {
				params.setRepositoryConfiguration(ExecutionRepositoryConfiguration.getDefault());
			}
		} else {
			ExecutionRepositoryConfiguration.Builder repoconfigbuilder;
			if (repositoryCollector.noNestRepository) {
				repoconfigbuilder = ExecutionRepositoryConfiguration.builder();
			} else {
				repoconfigbuilder = ExecutionRepositoryConfiguration
						.builder(ExecutionRepositoryConfiguration.getDefault());
			}
			for (RepositoryParam repoparam : repositoryCollector.getRepositories()) {
				ClassPathParam cp = repoparam.getClassPath();
				ClassPathLocation cplocation = createClassPathLocation(PARAM_NAME_REPOSITORY, cp, remoteenv, localenv,
						connections, pathconfiguration, true);
				String repoid = repoparam.getRepositoryIdentifier();
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceenumerator = repoparam
						.getServiceEnumerator();
				if (serviceenumerator == null) {
					serviceenumerator = new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class);
				}
				repoconfigbuilder.add(
						new ExecutionRepositoryConfiguration.RepositoryConfig(cplocation, repoid, serviceenumerator));
			}
			params.setRepositoryConfiguration(repoconfigbuilder.build());
		}
		Collection<TaskInvoker> taskinvokers = new ArrayList<>();
		if (!clusters.isEmpty()) {
			for (String clusterconnectionname : clusters) {
				if (RESERVED_CONNECTION_NAMES.contains(clusterconnectionname)) {
					throw new InvalidArgumentValueException(
							"Reserved cluster connection name: " + clusterconnectionname, PARAM_NAME_CLUSTER);
				}
				RemoteDaemonConnection clusterconn = connections.get(clusterconnectionname);
				if (clusterconn == null) {
					throw new InvalidArgumentValueException(
							"Daemon connection not found for cluster: " + clusterconnectionname, PARAM_NAME_CLUSTER);
				}
				TaskInvoker clustertaskinvoker = clusterconn.getClusterTaskInvoker();
				if (clustertaskinvoker == null) {
					throw new InvalidBuildConfigurationException(
							"Daemon doesn't support using it as build cluster: " + clusterconnectionname);
				}
				taskinvokers.add(clustertaskinvoker);
			}
		}
		if (clusterUseClients) {
			DaemonEnvironment useenv;
			if (remoteenv != null) {
				useenv = remoteenv;
			} else if (localenv != null) {
				useenv = localenv;
			} else {
				SakerLog.warning().out(System.out).println(
						PARAM_NAME_CLUSTER_USE_CLIENTS + " was specified, but not using daemon for build execution.");
				useenv = null;
			}
			if (useenv != null) {
				taskinvokers.addAll(useenv.getClientClusterTaskInvokers());
			}
		}
		params.setTaskInvokers(taskinvokers);
		return params;
	}

	private ExecutionPathConfiguration createPathConfiguration(DaemonEnvironment remoteenv, DaemonEnvironment localenv,
			SakerPath workingdir, NavigableMap<String, ? extends RemoteDaemonConnection> connections)
			throws IOException {
		if (mount.isEmpty()) {
			SakerFileProvider localfp;
			if (localenv != null) {
				localfp = localenv.getFileProvider();
			} else {
				localfp = LocalFileProvider.getInstance();
			}
			ExecutionPathConfiguration.Builder builder = ExecutionPathConfiguration
					.builder(DEFAULT_WORKING_DIRECTORY_PATH);
			builder.addRootProvider(DEFAULT_WORKING_DIRECTORY_ROOT,
					DirectoryMountFileProvider.create(localfp, workingdir, DEFAULT_WORKING_DIRECTORY_ROOT));
			return builder.build();
		}
		//at least one mounting is specified
		ExecutionPathConfiguration.Builder pathconfigbuilder = ExecutionPathConfiguration.builder(workingdir);
		for (DirectoryMountParam dm : mount) {
			DaemonPath path = dm.path;
			String root = dm.root;
			ProviderHolderPathKey mountpathkey = mountPathToPathKey(PARAM_NAME_MOUNT, path, remoteenv, localenv,
					connections);

			pathconfigbuilder.addRootProvider(root,
					DirectoryMountFileProvider.create(mountpathkey.getFileProvider(), mountpathkey.getPath(), root));
		}
		return pathconfigbuilder.build();
	}

	private ProviderHolderPathKey mountPathToPathKey(String argname, DaemonPath path, DaemonEnvironment remoteenv,
			DaemonEnvironment localenv, NavigableMap<String, ? extends RemoteDaemonConnection> connections) {
		String clientname = path.getClientName();
		SakerFileProvider mountfp;
		SakerPath mountedmpath = path.getPath();
		if (clientname == null) {
			mountfp = getLocalFileProviderFromLocalDaemon(localenv);
		} else {
			switch (clientname) {
				case DAEMON_CLIENT_NAME_LOCAL: {
					mountfp = getLocalFileProviderFromLocalDaemon(localenv);
					break;
				}
				case DAEMON_CLIENT_NAME_REMOTE: {
					if (remoteenv == null) {
						//if there is no remote daemon, then we are running only on local
						//therefore consider the local as the remote
						mountfp = getLocalFileProviderFromLocalDaemon(localenv);
					} else {
						mountfp = remoteenv.getFileProvider();
					}
					break;
				}
				case DAEMON_CLIENT_NAME_PROC_WORK_DIR: {
					mountfp = getLocalFileProviderFromLocalDaemon(localenv);
					mountedmpath = getUserDir().resolve(mountedmpath.forcedRelative());
					break;
				}
				default: {
					RemoteDaemonConnection connection = connections.get(clientname);
					if (connection == null) {
						throw new InvalidArgumentValueException("Connection not found with name: " + clientname,
								argname);
					}
					mountfp = connection.getDaemonEnvironment().getFileProvider();
					break;
				}
			}
		}
		return new SimpleProviderHolderPathKey(mountfp, mountedmpath);
	}

	private static SakerFileProvider getLocalFileProviderFromLocalDaemon(DaemonEnvironment localenv) {
		return localenv == null ? LocalFileProvider.getInstance() : localenv.getFileProvider();
	}

	private static RootFileProviderKey getRootFileProvideKeyOfConnection(String argname, String connectionname,
			DaemonEnvironment remoteenv, DaemonEnvironment localenv,
			NavigableMap<String, ? extends RemoteDaemonConnection> connections) {
		switch (connectionname) {
			case DAEMON_CLIENT_NAME_LOCAL: {
				return SakerPathFiles.getRootFileProviderKey(getLocalFileProviderFromLocalDaemon(localenv));
			}
			case DAEMON_CLIENT_NAME_REMOTE: {
				if (remoteenv == null) {
					//if there is no remote daemon, then we are running only on local
					//therefore consider the local as the remote
					return SakerPathFiles.getRootFileProviderKey(getLocalFileProviderFromLocalDaemon(localenv));
				}
				return SakerPathFiles.getRootFileProviderKey(remoteenv.getFileProvider());
			}
			default: {
				RemoteDaemonConnection connection = connections.get(connectionname);
				if (connection == null) {
					throw new InvalidArgumentValueException("Connection not found with name: " + connectionname,
							argname);
				}
				return SakerPathFiles.getRootFileProviderKey(connection.getDaemonEnvironment().getFileProvider());
			}
		}
	}

	private static void verifyDaemonConfiguration(DaemonLaunchParameters requestedlaunchparams,
			RemoteDaemonConnection daemonconnection) throws InvalidBuildConfigurationException {
		DaemonLaunchParameters actualruntimeconfig = daemonconnection.getDaemonEnvironment()
				.getRuntimeLaunchConfiguration();
		//the environment user parameters and storage directory must match
		boolean userparamsmatch = requestedlaunchparams.getUserParameters()
				.equals(actualruntimeconfig.getUserParameters());
		boolean storagedirmatch = Objects.equals(requestedlaunchparams.getStorageDirectory(),
				actualruntimeconfig.getStorageDirectory());
		if (!userparamsmatch || !storagedirmatch) {
			StringBuilder sb = new StringBuilder();
			sb.append("Configuration mismatch between build arguments and running daemon configuration (port ");
			sb.append(actualruntimeconfig.getPort());
			sb.append(").");
			if (!storagedirmatch) {
				sb.append(" Storage directories differ. Actual: " + actualruntimeconfig.getStorageDirectory()
						+ " expected: " + requestedlaunchparams.getStorageDirectory() + ".");
			}
			if (!userparamsmatch) {
				sb.append(" Enironment user parameters differ. Actual: " + actualruntimeconfig.getUserParameters()
						+ " expected: " + requestedlaunchparams.getUserParameters() + ".");
			}
			//Note: carefully change this exception type, as callers rely on its type
			throw new InvalidBuildConfigurationException(sb.toString());
		}
	}

	private static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams, AuthKeystoreParamContext authparams) throws IOException {
		return StartDaemonCommand.connectOrCreateDaemon(javaexe, sakerjarpath, launchparams, authparams);
	}

	private ClassPathLocation createClassPathLocation(String argname, ClassPathParam cp, DaemonEnvironment remoteenv,
			DaemonEnvironment localenv, NavigableMap<String, ? extends RemoteDaemonConnection> connections,
			ExecutionPathConfiguration pathconfiguration, boolean allownest) throws IOException {
		String cpstr = cp.getPath();
		if (cpstr.startsWith("http://") || cpstr.startsWith("https://")) {
			URL url = new URL(cpstr);
			return new HttpUrlJarFileClassPathLocation(url);
		}
		if (allownest && cpstr.startsWith("nest:/")) {
			return getNestRepositoryClassPathForNestVersionPath(argname, cpstr);
		}
		DaemonPath cpdaemonpath;
		try {
			cpdaemonpath = DaemonPath.valueOf(cpstr);
		} catch (IllegalArgumentException e) {
			throw new InvalidArgumentFormatException(e, argname);
		}
		String cpclientname = cpdaemonpath.getClientName();
		ProviderHolderPathKey repopathkey;
		if (cpclientname == null) {
			repopathkey = pathconfiguration.getPathKey(cpdaemonpath.getPath());
		} else {
			SakerFileProvider fp;
			switch (cpclientname) {
				case DAEMON_CLIENT_NAME_LOCAL: {
					fp = getLocalFileProviderFromLocalDaemon(localenv);
					repopathkey = SakerPathFiles.getPathKey(fp, cpdaemonpath.getPath());
					break;
				}
				case DAEMON_CLIENT_NAME_REMOTE: {
					if (remoteenv == null) {
						//if there is no remote daemon, then we are running only on local
						//therefore consider the local as the remote
						fp = getLocalFileProviderFromLocalDaemon(localenv);
					} else {
						fp = remoteenv.getFileProvider();
					}
					repopathkey = SakerPathFiles.getPathKey(fp, cpdaemonpath.getPath());
					break;
				}
				case DAEMON_CLIENT_NAME_PROC_WORK_DIR: {
					fp = getLocalFileProviderFromLocalDaemon(localenv);
					repopathkey = SakerPathFiles.getPathKey(fp,
							getUserDir().resolve(cpdaemonpath.getPath().forcedRelative()));
					break;
				}
				default: {
					RemoteDaemonConnection connection = connections.get(cpclientname);
					if (connection == null) {
						throw new InvalidArgumentValueException("Connection not found with name: " + cpclientname,
								argname);
					}
					fp = connection.getDaemonEnvironment().getFileProvider();
					repopathkey = SakerPathFiles.getPathKey(fp, cpdaemonpath.getPath());
					break;
				}
			}
		}
		return new JarFileClassPathLocation(repopathkey);
	}

	public static ClassPathLocation getNestRepositoryClassPathForNestVersionPath(String argname, String repostr)
			throws IOException {
		SakerPath path;
		try {
			path = SakerPath.valueOf(repostr);
		} catch (IllegalArgumentException e) {
			throw new InvalidArgumentFormatException("Failed to parse nest:/ repository classpath path: " + repostr, e,
					argname);
		}
		if (path.getNameCount() != 2 || !path.getName(0).equals("version")) {
			throw new InvalidArgumentValueException("Invalid nest:/ repository classpath path. "
					+ "It should have the format nest:/version/<version-number> or be nest:/version/latest: " + repostr,
					argname);
		}
		String versionname = path.getName(1);
		if ("latest".equals(versionname)) {
			HttpURLConnection connection = (HttpURLConnection) new URL(
					"https://mirror.nest.saker.build/badges/saker.nest/latest.txt").openConnection();
			connection.setDoOutput(false);
			int rc = connection.getResponseCode();
			if (rc != HttpURLConnection.HTTP_OK) {
				throw new ArgumentResolutionException(
						"Failed to determine latest saker.nest repository version. Server responded with code: " + rc,
						argname);
			}
			String version;
			try (InputStream is = connection.getInputStream()) {
				version = StreamUtils.readStreamStringFully(is, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new ArgumentResolutionException(
						"Failed to determine latest saker.nest repository version. Failed to read server response.", e,
						argname);
			}
			try {
				return NestRepositoryClassPathLocation.getInstance(version);
			} catch (IllegalArgumentException e) {
				throw new ArgumentResolutionException(
						"Saker.nest repository server returned invalid version number: " + version, e, argname);
			}
		}
		try {
			return NestRepositoryClassPathLocation.getInstance(versionname);
		} catch (IllegalArgumentException e) {
			throw new InvalidArgumentFormatException(
					"Invalid nest:/version/<version-number> repository classpath path. Invalid version number: "
							+ repostr,
					e, argname);
		}
	}

	private static NavigableSet<SakerPath> getBuildFileNamesInDirectory(SakerPath directory,
			ExecutionScriptConfiguration scriptconfiguration, SakerFileProvider wdfp) throws IOException {
		NavigableSet<SakerPath> buildfiles = new TreeSet<>();
		//check only files, not directories
		NavigableMap<String, ? extends FileEntry> direntries = wdfp.getDirectoryEntries(directory);
		for (Entry<String, ? extends FileEntry> entry : direntries.entrySet()) {
			if (!entry.getValue().isRegularFile()) {
				continue;
			}
			String f = entry.getKey();
			try {
				SakerPath fpath = directory.resolve(f);
				if (scriptconfiguration.getScriptOptionsConfig(fpath) != null) {
					buildfiles.add(fpath);
				}
			} catch (InvalidPathFormatException e) {
				//the file name may be invalid. e.g. if it contains a colon
				//we cannot really handle them in the build system, but don't fail here
				//the exception should be thrown down the line
				continue;
			}
		}
		return buildfiles;
	}

	/**
	 * @cmd-format &lt;type>
	 */
	public static ContentDescriptorSupplier getContentDescriptorSupplier(String argname, ParsingIterator it) {
		return getContentDescriptorSupplier(argname, ParseUtil.requireNextArgument(argname, it));
	}

	public static ContentDescriptorSupplier getContentDescriptorSupplier(String argname, String type) {
		switch (type.toLowerCase(Locale.ENGLISH)) {
			case "attr": {
				return CommonContentDescriptorSupplier.FILE_ATTRIBUTES;
			}
			case "md5": {
				return CommonContentDescriptorSupplier.HASH_MD5;
			}
			default: {
				throw new InvalidArgumentValueException("Invalid content type: " + type + " Expected attr or md5.",
						argname);
			}
		}
	}

	private static final class ConsoleSecretInputReader implements SecretInputReader {
		private final Console console;

		private ConsoleSecretInputReader(Console console) {
			this.console = console;
		}

		@Override
		public String readSecret(String titleinfo, String message, String prompt, String secretidentifier) {
			PrintWriter writer = console.writer();
			if (titleinfo != null) {
				writer.println(titleinfo);
			}
			if (message != null) {
				writer.println(message);
			}
			if (prompt != null) {
				writer.print(prompt);
			}
			writer.flush();
			char[] secret = console.readPassword();
			if (secret == null) {
				return null;
			}
			return new String(secret);
		}
	}

	public static class RepositoryCollector {

		private Collection<RepositoryParam> repositories = new ArrayList<>();
		private RepositoryParam lastRepository;
		/**
		 * <pre>
		 * Flag to specify that the saker.nest repository shouldn't be 
		 * automatically included for the build.
		 * 
		 * The saker.nest repository is the default repository associated with the build
		 * system. Specify this flag to disable its automatic inclusion.
		 * </pre>
		 */
		@Flag
		@Parameter({ "-repository-no-nest", "-repo-no-nest" })
		public boolean noNestRepository = false;

		public Collection<RepositoryParam> getRepositories() {
			return repositories;
		}

		public boolean isEmpty() {
			return repositories.isEmpty();
		}

		/**
		 * <pre>
		 * Starts a repository configuration with a given classpath.
		 *
		 * The classpath may be an HTTP URL by starting it with the 
		 * 'http://' or 'https://' phrases. 
		 * It can also be a file path in the format specified by -mount. 
		 * The paths are resolved against the path configuration of the build.
		 * 
		 * It can also be in the format of 'nest:/version/&lt;version-number&gt;'
		 * where the &lt;version-number&gt; is the version of the saker.nest repository 
		 * you want to use. The &lt;version-number&gt; can also be 'latest' in which 
		 * case the most recent known saker.nest nest repository release is used.
		 * 
		 * Any following -repository parameters will modify this configuration.
		 * </pre>
		 * 
		 * @cmd-help-meta [repository]
		 */
		@Parameter({ PARAM_NAME_REPOSITORY, "-repo" })
		public void repository(RepositoryParam repoparam) {
			repositories.add(repoparam);
			lastRepository = repoparam;
		}

		/**
		 * <pre>
		 * Specifies the name of the repository class for the previously
		 * started repository configuration.
		 * 
		 * The class should be an instance of 
		 * saker.build.runtime.repository.SakerRepositoryFactory.
		 * 
		 * If not specified, the service configuration of the classpath will 
		 * be used to load the repository. See the ServiceLoader Java class
		 * for more information.
		 * </pre>
		 * 
		 * @cmd-format &lt;class name>
		 * @cmd-help-meta [repository]
		 */
		@Parameter({ PARAM_NAME_REPOSITORY_CLASS, "-repo-class" })
		public void repositoryClass(String classname) {
			requireLastRepository(PARAM_NAME_REPOSITORY_CLASS);
			if (lastRepository.getServiceEnumerator() != null) {
				throw new InvalidArgumentValueException("Repository class name specified multiple times for: "
						+ lastRepository.getClassPath().getPath(), PARAM_NAME_REPOSITORY_CLASS);
			}
			lastRepository.setServiceEnumerator(
					new NamedCheckingClassPathServiceEnumerator<>(classname, SakerRepositoryFactory.class));
		}

		/**
		 * <pre>
		 * Specifies the identifier of the repository for the previously
		 * started repository configuration.
		 * 
		 * The repository identifier should be unique for each repository
		 * and they can be used to differentiate them in appropriate contexts.
		 * 
		 * E.g. when invoking tasks, the &#064;repositoryid syntax can be used to
		 * specify where to look for the task implementation.
		 * </pre>
		 * 
		 * @cmd-help-meta [repository]
		 */
		@Parameter({ PARAM_NAME_REPOSITORY_ID, "-repo-id" })
		public void repositoryId(String id) {
			requireLastRepository(PARAM_NAME_REPOSITORY_ID);
			if (lastRepository.getRepositoryIdentifier() != null) {
				throw new InvalidArgumentValueException("Repository identifier specified multiple times for: "
						+ lastRepository.getClassPath().getPath(), PARAM_NAME_REPOSITORY_ID);
			}
			lastRepository.setRepositoryIdentifier(id);
		}

		private void requireLastRepository(String currentarg) {
			if (lastRepository == null) {
				throw new MissingArgumentException(
						currentarg + " requires a repository configuration to be applied to. Specify "
								+ PARAM_NAME_REPOSITORY + " before using " + currentarg + ".",
						currentarg);
			}
		}
	}

	public static class DatabaseConfigParam {
		protected String connectionName;
		protected WildcardPath wildcard;
		protected ContentDescriptorSupplier descriptorSupplier;

		public DatabaseConfigParam(String connectionName, WildcardPath wildcard,
				ContentDescriptorSupplier descriptorSupplier) {
			this.connectionName = connectionName;
			this.wildcard = wildcard;
			this.descriptorSupplier = descriptorSupplier;
		}

		public static DatabaseConfigParam parse(String argname, ParsingIterator it) {
			Objects.requireNonNull(it, "iterator");

			String connectionname;
			String wildcard;
			ContentDescriptorSupplier type;

			if (!it.hasNext()) {
				throw new MissingArgumentException("Connection name argument is missing.", argname);
			}
			connectionname = it.next();
			if (!it.hasNext()) {
				throw new MissingArgumentException("Wildcard argument is missing.", argname);
			}
			wildcard = it.next();
			if (!it.hasNext()) {
				throw new MissingArgumentException("Type argument is missing.", argname);
			}
			type = getContentDescriptorSupplier(argname, it.next());
			WildcardPath wildcardpath;
			try {
				wildcardpath = WildcardPath.valueOf(wildcard);
			} catch (IllegalArgumentException e) {
				throw new InvalidArgumentFormatException("Invalid wildcard format for: " + wildcard, e, argname);
			}
			return new DatabaseConfigParam(connectionname, wildcardpath, type);
		}

	}

	public static class DatabaseConfigurationCollection {

		/**
		 * <pre>
		 * Specifies the file content change detection mechanism for the
		 * build database.
		 * 
		 * The specified mechanism will be used to determine if the contents
		 * of a given file has been changed between build executions. Accepted
		 * values are the following:
		 * 	attr
		 * 		The file attributes and size will be used to compare the state
		 * 		of the file.
		 * 		This is the default value for the build system.
		 * 	md5
		 * 		The contents of the files will be hashed using MD5, and the 
		 * 		hash will be compared to the previous state of the file.
		 * 
		 * This parameter specifies the mechanism for the files which have not
		 * been matched by any of the wildcards specified using -dbconfig-path.
		 * 
		 * To specify a mechanism for all files, use this parameter, and don't
		 * specify anything for -dbconfig-path.
		 * </pre>
		 */
		@Parameter("-dbconfig-fallback")
		@Converter(method = "getContentDescriptorSupplier", converter = BuildCommand.class)
		public ContentDescriptorSupplier fallbackContentDescriptorSupplier;
		private List<DatabaseConfigParam> configParams = new ArrayList<>();

		public boolean isEmpty() {
			return fallbackContentDescriptorSupplier == null && configParams.isEmpty();
		}

		public ContentDescriptorSupplier getFallbackContentDescriptorSupplier() {
			if (fallbackContentDescriptorSupplier == null) {
				return DatabaseConfiguration.getDefaultFallbackContentDescriptorSupplier();
			}
			return fallbackContentDescriptorSupplier;
		}

		public List<DatabaseConfigParam> getConfigParams() {
			return configParams;
		}

		/**
		 * <pre>
		 * Specifies the file content change detection mechanism for the
		 * build database for the files matched by the given wildcard.
		 * 
		 * The &lt;type> argument accepts the same values as -dbconfig-fallback.
		 * 
		 * The &lt;wildcard> applies to all files that is accesible through the
		 * given connection. 
		 * 
		 * The &lt;connection-name> parameter is interpreted in 
		 * the same way as in name part of -mount parameter paths.
		 * </pre>
		 * 
		 * @cmd-format &lt;connection-name> &lt;wildcard> &lt;type>
		 */
		@Parameter(PARAM_NAME_DBCONFIG_PATH)
		@Converter(method = "parse", converter = DatabaseConfigParam.class)
		public void databaseConfig(DatabaseConfigParam param) {
			configParams.add(param);
		}

	}

	public static class ScriptConfigCollector {

		public static class ConfigEntry {
			public final WildcardPath files;
			public Map<String, String> options = new LinkedHashMap<>();
			public ClassPathParam classPath;
			public ClassPathServiceEnumerator<? extends ScriptAccessProvider> serviceEnumerator;

			public ConfigEntry(WildcardPath files) {
				this.files = files;
			}
		}

		private Collection<ConfigEntry> configs = new ArrayList<>();
		private ConfigEntry lastConfig;

		public Collection<ConfigEntry> getConfigs() {
			return configs;
		}

		public boolean isEmpty() {
			return lastConfig == null;
		}

		/**
		 * <pre>
		 * Starts a script configuration that applies to the files specified
		 * by the parameter wildcard.
		 * 
		 * Script configurations specify how a given build script should be
		 * parsed when used during the build. If the specified wildcard matches
		 * the full absolute path of the build script, then it will be used
		 * to parse the script.
		 * 
		 * Scripts configurations allow different languages to be used and enable
		 * configuring custom options for them.
		 * 
		 * Any following -script and -SO parameters will modify this configuration.
		 * </pre>
		 * 
		 * @cmd-format &lt;wildcard>
		 * @cmd-help-meta [script]
		 */
		@Parameter(PARAM_NAME_SCRIPT_FILES)
		public void scriptFiles(String filespattern) {
			ConfigEntry config = new ConfigEntry(WildcardPath.valueOf(filespattern));
			configs.add(config);
			lastConfig = config;
		}

		/**
		 * <pre>
		 * Specifies the classpath of the script parser for the
		 * previously started script configuration.
		 * 
		 * The classpath is in the same format as in -repository parameter.
		 * </pre>
		 * 
		 * @cmd-help-meta [script]
		 */
		@Parameter({ PARAM_NAME_SCRIPT_CLASSPATH, "-script-cp" })
		public void scriptClassPath(ClassPathParam classpath) {
			requireLastConfig(PARAM_NAME_SCRIPT_CLASSPATH);
			if (lastConfig.classPath != null) {
				throw new InvalidArgumentValueException(
						"Script configuration classpath specified multiple times for: " + lastConfig.files,
						PARAM_NAME_SCRIPT_CLASSPATH);
			}
			lastConfig.classPath = classpath;
		}

		/**
		 * <pre>
		 * Specifies the class name of the script parser for the
		 * previously started script configuration.
		 * 
		 * The class should be an instance of 
		 * saker.build.scripting.ScriptAccessProvider.
		 * 
		 * If not specified, the service configuration of the classpath will 
		 * be used to load the parser. See the ServiceLoader Java class
		 * for more information.
		 * </pre>
		 * 
		 * @cmd-format &lt;class name>
		 * @cmd-help-meta [script]
		 */
		@Parameter(PARAM_NAME_SCRIPT_CLASS)
		public void scriptAccessorClassName(String classname) {
			requireLastConfig(PARAM_NAME_SCRIPT_CLASS);
			if (lastConfig.serviceEnumerator != null) {
				throw new InvalidArgumentValueException(
						"Script configuration class name specified multiple times for: " + lastConfig.files,
						PARAM_NAME_SCRIPT_CLASS);
			}
			lastConfig.serviceEnumerator = new NamedCheckingClassPathServiceEnumerator<>(classname,
					ScriptAccessProvider.class);
		}

		/**
		 * <pre>
		 * Specifies a script option for the previously started script configuration.
		 * 
		 * Script options are string key-value pairs that are interpreted by the
		 * script parser in an implementation dependent way.
		 * 
		 * See the documentation of the associated scripting language to see
		 * what kind of options they accept.
		 * </pre>
		 * 
		 * @cmd-help-meta [script]
		 */
		@Parameter(PARAM_NAME_SO)
		public void scriptOption(String key, String value) {
			requireLastConfig(PARAM_NAME_SO);
			if (lastConfig.options.containsKey(key)) {
				throw new InvalidArgumentValueException(
						"Script option defined multiple times: " + key + " for " + lastConfig.files, PARAM_NAME_SO);
			}
			lastConfig.options.put(key, value);
		}

		private void requireLastConfig(String currentarg) {
			if (lastConfig == null) {
				throw new MissingArgumentException(
						currentarg + " requires a script configuration to be applied to. Specify "
								+ PARAM_NAME_SCRIPT_FILES + " before using " + currentarg + ".",
						currentarg);
			}
		}
	}
}