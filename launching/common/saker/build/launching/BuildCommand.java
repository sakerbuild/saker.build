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
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.net.SocketFactory;

import saker.build.daemon.BuildExecutionInvoker;
import saker.build.daemon.DaemonEnvironment;
import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.EnvironmentBuildExecutionInvoker;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.daemon.files.DaemonPath;
import saker.build.exception.BuildExecutionFailedException;
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
import saker.build.runtime.environment.EnvironmentParameters;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.runtime.execution.SakerLog.ExceptionFormat;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.params.NestRepositoryClassPathLocation;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.util.exc.ExceptionView;
import saker.build.util.java.JavaTools;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.MultiParameter;
import sipka.cmdline.api.NameSubstitution;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.api.PositionalParameter;
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

	private static final String DEFAULT_BUILD_FILE_NAME = "saker.build";
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
	@Parameter
	@MultiParameter(DirectoryMountParam.class)
	public Collection<DirectoryMountParam> mount = new LinkedHashSet<>();

	private Map<String, String> userParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Species user parameters for the build execution.
	 * 
	 * User parameters are arbitrary key-value pairs which can be used to dynamically
	 * configure different aspects of the build system. This is usually applicable for
	 * configuring repositories or specific tasks you're executing.
	 * 
	 * Can be used multiple times to define multiple entries.
	 * </pre>
	 */
	@Parameter("-U")
	public void userParameter(String key, String value) {
		if (userParameters.containsKey(key)) {
			throw new IllegalArgumentException("User parameter specified multiple times: " + key);
		}
		userParameters.put(key, value);
	}

	@ParameterContext
	public RepositoryCollector repositoryCollector = new RepositoryCollector();

	@ParameterContext
	public ScriptConfigCollector scriptConfigCollector = new ScriptConfigCollector();

	@ParameterContext
	public DatabaseConfigurationCollection databaseConfigCollector = new DatabaseConfigurationCollection();

	/**
	 * <pre>
	 * Specifies a daemon connection that should be used as a cluster for the build
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
	@Parameter({ "-cluster" })
	@MultiParameter(String.class)
	public Set<String> clusters = new LinkedHashSet<>();

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
	@Parameter
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
	 * Specifies the address of the daemon that should be used for build execution.
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
	@Parameter("-daemon-address")
	public DaemonAddressParam daemonAddress;

	@ParameterContext(substitutions = { @NameSubstitution(pattern = "-(.*)", replacement = "-daemon-$1") })
	public GeneralDaemonParams instantiateDaemonParams = new GeneralDaemonParams();

	@ParameterContext
	public EnvironmentParams envParams = new EnvironmentParams();

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
	 * Specifies the interval of the polling based deadlock detection mechanism.
	 * 
	 * The build system actively detects any deadlocks occurring during task
	 * executions. The waiting tasks will be woken up in regular intervals based
	 * on this parameter.
	 * 
	 * The value is expected in milliseconds, default is 3000 (3 seconds).
	 * </pre>
	 */
	@Parameter("-deadlock-polling-millis")
	public Long deadlockPollingMillis = null;

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
	 */
	@Parameter("-trace")
	public String buildTracePath;

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

	public void call() throws Exception {
		if (this.daemon || daemonAddress != null) {
			if (daemonAddress != null) {
				//there is an address specified, only create a local daemon if necessary, and -daemon is specified

				InetSocketAddress remoteaddress = daemonAddress.getSocketAddress();
				if (this.daemon) {
					//we're allowed to create a local daemon if necessary
					if (isLocalAddress(remoteaddress.getAddress())) {
						//we're connecting to a local daemon
						try (RemoteDaemonConnection connection = connectOrCreateDaemon(
								JavaTools.getCurrentJavaExePath(), sakerJarLocator.getSakerJarPath(),
								getLaunchDaemonParameters())) {
							DaemonEnvironment daemonenv = connection.getDaemonEnvironment();
							runBuild(daemonenv, null);
						}
					} else {
						//we're using a non-local daemon to build
						if (useDaemonCache) {
							//we want to use caching for the project, so we need a persistent connection to the build daemon
							//this is in order so the file watching and stuff works properly
							//connect or create a daemon and connect via that
							DaemonLaunchParameters localdaemonparams = getLaunchDaemonParameters();
							//it is required for the local daemon to act as a client
							try (RemoteDaemonConnection localconnection = connectOrCreateDaemon(
									JavaTools.getCurrentJavaExePath(), sakerJarLocator.getSakerJarPath(),
									localdaemonparams)) {
								DaemonEnvironment env = localconnection.getDaemonEnvironment();
								try (RemoteDaemonConnection remoteconnection = env.connectTo(remoteaddress)) {
									runBuild(remoteconnection.getDaemonEnvironment(),
											localconnection.getDaemonEnvironment());
								}
							}
						} else {
							//we don't use caching, so we can just directly connect to the daemon
							try (RemoteDaemonConnection connection = RemoteDaemonConnection.connect(remoteaddress)) {
								runBuild(connection.getDaemonEnvironment(), null);
							}
						}
					}
				} else {
					//we're not allowed to start a new daemon
					//just directly connect to the daemon address
					try (RemoteDaemonConnection connection = RemoteDaemonConnection.connect(remoteaddress)) {
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

				DaemonLaunchParameters launchparams = getLaunchDaemonParameters();
				try (RemoteDaemonConnection connection = connectOrCreateDaemon(JavaTools.getCurrentJavaExePath(),
						sakerJarLocator.getSakerJarPath(), launchparams)) {
					runBuild(null, connection.getDaemonEnvironment());
				}
			}
		} else {
			// not using daemon
			EnvironmentParameters modenvparams = EnvironmentParameters.builder(sakerJarLocator.getSakerJarPath())
					.setUserParameters(envParams.getEnvironmentUserParameters())
					.setThreadFactor(envParams.getThreadFactor())
					.setStorageDirectory(instantiateDaemonParams.getStorageDirectoryPath()).build();
			try (SakerEnvironmentImpl sakerenv = new SakerEnvironmentImpl(modenvparams)) {
				sakerenv.redirectStandardIO();
				runBuild(null, null, new EnvironmentBuildExecutionInvoker(sakerenv));
			}
		}
	}

	private DaemonLaunchParameters getLaunchDaemonParameters() {
		return instantiateDaemonParams.toLaunchParameters(envParams);
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
			if (!connect.isEmpty()) {
				connections = new ConcurrentSkipListMap<>();
				if (builddaemonenv == null) {
					//XXX make this socket factory configureable
					SocketFactory socketfactory = SocketFactory.getDefault();
					ThreadUtils.runParallelItems(connect, c -> {
						if (RESERVED_CONNECTION_NAMES.contains(c.name)) {
							throw new IllegalArgumentException(c.name + " is a reserved connection name.");
						}
						RemoteDaemonConnection connection = RemoteDaemonConnection.connect(socketfactory,
								c.address.getSocketAddress());
						rescloser.add(connection);
						RemoteDaemonConnection prevc = connections.put(c.name, connection);
						if (prevc != null) {
							throw new IllegalArgumentException("Multiple connections specified with name: " + c.name);
						}
					});
				} else {
					ThreadUtils.runParallelItems(connect, c -> {
						if (RESERVED_CONNECTION_NAMES.contains(c.name)) {
							throw new IllegalArgumentException(c.name + " is a reserved connection name.");
						}
						RemoteDaemonConnection connection = builddaemonenv.connectTo(c.address.getSocketAddress());
						rescloser.add(connection);
						RemoteDaemonConnection prevc = connections.put(c.name, connection);
						if (prevc != null) {
							throw new IllegalArgumentException("Multiple connections specified with name: " + c.name);
						}
					});
				}
			} else {
				connections = Collections.emptyNavigableMap();
			}

			SakerPath workingdir = getWorkingDirectory();

			ExecutionParametersImpl params = createExecutionParameters(envcontroller, remoteenv, localenv, workingdir,
					connections);

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
						throw new IllegalArgumentException("Failed to determine build file to use: " + buildfiles);
					}
					this.buildScriptFile = buildfiles.first();
				}
			}
			long nanos = System.nanoTime();
			BuildTaskExecutionResult execres = envcontroller.run(this.buildScriptFile, this.target, params, project);
			long endnanos = System.nanoTime();
			ExceptionView excview = execres.getPositionedExceptionView();
			if (excview != null) {
				//XXX option for not omitting transitives?
				TaskUtils.printTaskExceptionsOmitTransitive(excview, System.err, workingdir, getStackTraceFormat());
				//do not retrieve the real exception, as that may not be RMI transferrable.
				String msg = excview.getMessage();
				throw new BuildExecutionFailedException(
						excview.getExceptionClassName() + (ObjectUtils.isNullOrEmpty(msg) ? "" : ": " + msg));
			}
			System.out.println("Build finished. (" + DateUtils.durationToString((endnanos - nanos) / 1_000_000) + ")");

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
			throw new AssertionError("No remote and no local daemon for build.");
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
		if (deadlockPollingMillis != null) {
			params.setDeadlockPollingFrequencyMillis(deadlockPollingMillis);
		}
		if (buildTracePath != null) {
			ProviderHolderPathKey buildtraceoutpathkey = mountPathToPathKey(DaemonPath.valueOf(buildTracePath),
					remoteenv, localenv, connections);
			params.setBuildTraceOutputPathKey(buildtraceoutpathkey);
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

		BuildInformation buildinfo = new BuildInformation();
		if (!ObjectUtils.isNullOrEmpty(connections)) {
			//XXX this information retrieval could be done multi-threadingly as it may involve multiple RMI calls
			TreeMap<String, UUID> machinesinfo = new TreeMap<>();
			for (Entry<String, ? extends RemoteDaemonConnection> entry : connections.entrySet()) {
				machinesinfo.put(entry.getKey(), SakerPathFiles
						.getRootFileProviderKey(entry.getValue().getDaemonEnvironment().getFileProvider()).getUUID());
			}
			buildinfo.setConnectedMachineNames(machinesinfo);
		}
		params.setBuildInfo(buildinfo);

		DatabaseConfiguration databaseconfiguration;
		if (databaseConfigCollector.isEmpty()) {
			databaseconfiguration = DatabaseConfiguration.getDefault();
		} else {
			DatabaseConfiguration.Builder builder = DatabaseConfiguration
					.builder(databaseConfigCollector.getFallbackContentDescriptorSupplier());
			for (DatabaseConfigParam confparam : databaseConfigCollector.getConfigParams()) {
				builder.add(
						getRootFileProvideKeyOfConnection(confparam.connectionName, remoteenv, localenv, connections),
						confparam.wildcard, confparam.descriptorSupplier);
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
					ClassPathLocation scriptclasspath = createClassPathLocation(scentry.classPath, remoteenv, localenv,
							connections, pathconfiguration, false);
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
				ClassPathLocation cplocation = createClassPathLocation(cp, remoteenv, localenv, connections,
						pathconfiguration, true);
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
		if (!clusters.isEmpty()) {
			Collection<TaskInvokerFactory> taskinvokerfactories = new ArrayList<>();
			for (String clusterconnectionname : clusters) {
				if (RESERVED_CONNECTION_NAMES.contains(clusterconnectionname)) {
					throw new IllegalArgumentException("Invalid cluster connection name: " + clusterconnectionname);
				}
				RemoteDaemonConnection clusterconn = connections.get(clusterconnectionname);
				if (clusterconn == null) {
					throw new IllegalArgumentException(
							"Daemon connection for cluster use not found: " + clusterconnectionname);
				}
				TaskInvokerFactory clustertaskinvokerfactory = clusterconn.getClusterTaskInvokerFactory();
				if (clustertaskinvokerfactory == null) {
					throw new IllegalArgumentException(
							"Daemon doesn't support cluster usage: " + clusterconnectionname);
				}
				taskinvokerfactories.add(clustertaskinvokerfactory);
			}
			params.setTaskInvokerFactories(taskinvokerfactories);
		}
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
			ProviderHolderPathKey mountpathkey = mountPathToPathKey(path, remoteenv, localenv, connections);

			pathconfigbuilder.addRootProvider(root,
					DirectoryMountFileProvider.create(mountpathkey.getFileProvider(), mountpathkey.getPath(), root));
		}
		return pathconfigbuilder.build();
	}

	private ProviderHolderPathKey mountPathToPathKey(DaemonPath path, DaemonEnvironment remoteenv,
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
						throw new IllegalArgumentException("Connection not found with name: " + clientname);
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

	private static RootFileProviderKey getRootFileProvideKeyOfConnection(String connectionname,
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
					throw new IllegalArgumentException("Connection not found with name: " + connectionname);
				}
				return SakerPathFiles.getRootFileProviderKey(connection.getDaemonEnvironment().getFileProvider());
			}
		}
	}

	private static boolean isLocalAddress(InetAddress address) {
		if (address.isLoopbackAddress()) {
			return true;
		}
		try {
			return NetworkInterface.getByInetAddress(address) != null;
		} catch (SocketException e) {
		}
		return false;
	}

	private static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams) throws IOException {
		RemoteDaemonConnection connected = StartDaemonCommand.connectOrCreateDaemon(javaexe, sakerjarpath,
				launchparams);
		RemoteDaemonConnection toclose = connected;
		try {
			DaemonLaunchParameters actualconfig = connected.getDaemonEnvironment().getRuntimeLaunchConfiguration();
			if (isDaemonSuitableForUse(launchparams, actualconfig)) {
				toclose = null;
				return connected;
			}
			throw new IOException("Cannot use running daemon at storage directory, configuration mismatch: "
					+ launchparams.getStorageDirectory() + " with requested " + launchparams + " and actual "
					+ actualconfig);
		} finally {
			IOUtils.close(toclose);
		}
	}

	private static boolean isDaemonSuitableForUse(DaemonLaunchParameters requestedlaunchparams,
			DaemonLaunchParameters actualruntimeconfig) {
		//the storage directories are the same for the two daemons
		//there is no breaking parameters that could cause a build to fail due to configuration
		//    port number is irrelevant regarding to the build
		//    it doesnt matter if the daemon acts as a server, as we're already connected to it
		//    the thread factor doesn't matter, as the daemon was created with a specific thread factor in order for the builds to use it

		//the environment user parameters are important
		if (!requestedlaunchparams.getUserParameters().equals(actualruntimeconfig.getUserParameters())) {
			return false;
		}
		return true;
	}

	private ClassPathLocation createClassPathLocation(ClassPathParam cp, DaemonEnvironment remoteenv,
			DaemonEnvironment localenv, NavigableMap<String, ? extends RemoteDaemonConnection> connections,
			ExecutionPathConfiguration pathconfiguration, boolean allownest) throws IOException {
		String repostr = cp.getPath();
		if (repostr.startsWith("http://") || repostr.startsWith("https://")) {
			URL url = new URL(repostr);
			return new HttpUrlJarFileClassPathLocation(url);
		}
		if (allownest && repostr.startsWith("nest:/")) {
			return getNestRepositoryClassPathForNestVersionPath(repostr);
		}
		DaemonPath repodaemonpath = DaemonPath.valueOf(repostr);
		String repoclientname = repodaemonpath.getClientName();
		ProviderHolderPathKey repopathkey;
		if (repoclientname == null) {
			repopathkey = pathconfiguration.getPathKey(repodaemonpath.getPath());
		} else {
			SakerFileProvider fp;
			switch (repoclientname) {
				case DAEMON_CLIENT_NAME_LOCAL: {
					fp = getLocalFileProviderFromLocalDaemon(localenv);
					repopathkey = SakerPathFiles.getPathKey(fp, repodaemonpath.getPath());
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
					repopathkey = SakerPathFiles.getPathKey(fp, repodaemonpath.getPath());
					break;
				}
				case DAEMON_CLIENT_NAME_PROC_WORK_DIR: {
					fp = getLocalFileProviderFromLocalDaemon(localenv);
					repopathkey = SakerPathFiles.getPathKey(fp,
							getUserDir().resolve(repodaemonpath.getPath().forcedRelative()));
					break;
				}
				default: {
					RemoteDaemonConnection connection = connections.get(repoclientname);
					if (connection == null) {
						throw new IllegalArgumentException("Connection not found with name: " + repoclientname);
					}
					fp = connection.getDaemonEnvironment().getFileProvider();
					repopathkey = SakerPathFiles.getPathKey(fp, repodaemonpath.getPath());
					break;
				}
			}
		}
		return new JarFileClassPathLocation(repopathkey);
	}

	public static ClassPathLocation getNestRepositoryClassPathForNestVersionPath(String repostr) throws IOException {
		SakerPath path;
		try {
			path = SakerPath.valueOf(repostr);
		} catch (IllegalArgumentException e) {
			throw new InvalidPathFormatException("Failed to parse nest:/ repository classpath path. (" + repostr + ")",
					e);
		}
		if (path.getNameCount() != 2 || !path.getName(0).equals("version")) {
			throw new InvalidPathFormatException(
					"Invalid nest:/ repository classpath path. It must be nest:/version/<version-number> or nest:/version/latest ("
							+ repostr + ")");
		}
		String versionname = path.getName(1);
		if ("latest".equals(versionname)) {
			HttpURLConnection connection = (HttpURLConnection) new URL(
					"https://mirror.nest.saker.build/badges/saker.nest/latest.txt").openConnection();
			connection.setDoOutput(false);
			int rc = connection.getResponseCode();
			if (rc != HttpURLConnection.HTTP_OK) {
				throw new IOException(
						"Failed to determine latest saker.nest repository version. Server responded with code: " + rc);
			}
			String version;
			try (InputStream is = connection.getInputStream()) {
				version = StreamUtils.readStreamStringFully(is, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IOException(
						"Failed to determine latest saker.nest repository version. Failed to read server response.", e);
			}
			try {
				return NestRepositoryClassPathLocation.getInstance(version);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Server returned invalid version number: " + version, e);
			}
		}
		try {
			return NestRepositoryClassPathLocation.getInstance(versionname);
		} catch (IllegalArgumentException e) {
			throw new InvalidPathFormatException(
					"Invalid nest:/version/<version-number> repository classpath path. Invalid version number. ("
							+ repostr + ")");
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
	public static ContentDescriptorSupplier getContentDescriptorSupplier(ParsingIterator it) {
		return getContentDescriptorSupplier(it.next());
	}

	public static ContentDescriptorSupplier getContentDescriptorSupplier(String type) {
		switch (type.toLowerCase(Locale.ENGLISH)) {
			case "attr": {
				return CommonContentDescriptorSupplier.FILE_ATTRIBUTES;
			}
			case "md5": {
				return CommonContentDescriptorSupplier.HASH_MD5;
			}
			default: {
				throw new IllegalArgumentException("Invalid content type: " + type);
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
		@Parameter({ "-repository", "-repo" })
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
		@Parameter({ "-repository-class", "-repo-class" })
		public void repositoryClass(String classname) {
			requireLastRepository();
			if (lastRepository.getServiceEnumerator() != null) {
				throw new IllegalArgumentException("Repository class name specified multiple times for: "
						+ lastRepository.getClassPath().getPath());
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
		@Parameter({ "-repository-id", "-repo-id" })
		public void repositoryId(String id) {
			requireLastRepository();
			if (lastRepository.getRepositoryIdentifier() != null) {
				throw new IllegalArgumentException("Repository identifier specified multiple times for: "
						+ lastRepository.getClassPath().getPath());
			}
			lastRepository.setRepositoryIdentifier(id);
		}

		private void requireLastRepository() {
			if (lastRepository == null) {
				throw new IllegalArgumentException("No applicable repository configuration.");
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

		public static DatabaseConfigParam parse(ParsingIterator it) {
			String connectionname = ParseUtil.requireNextArgument("-dbconfig-path", it);
			String wildcard = ParseUtil.requireNextArgument("-dbconfig-path", it);
			ContentDescriptorSupplier type = getContentDescriptorSupplier(
					ParseUtil.requireNextArgument("-dbconfig-path", it));
			WildcardPath wildcardpath;
			try {
				wildcardpath = WildcardPath.valueOf(wildcard);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Failed to parse wildcard for -dbconfig-path: " + wildcard, e);
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
		@Parameter("-dbconfig-path")
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
		@Parameter("-script-files")
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
		@Parameter({ "-script-classpath", "-script-cp" })
		public void scriptClassPath(ClassPathParam classpath) {
			requireLastConfig();
			if (lastConfig.classPath != null) {
				throw new IllegalArgumentException(
						"Script configuration classpath specified multiple times for: " + lastConfig.files);
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
		@Parameter("-script-class")
		public void scriptAccessorClassName(String classname) {
			requireLastConfig();
			if (lastConfig.serviceEnumerator != null) {
				throw new IllegalArgumentException(
						"Script configuration class name specified multiple times for: " + lastConfig.files);
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
		@Parameter("-SO")
		public void scriptOption(String key, String value) {
			requireLastConfig();
			if (lastConfig.options.containsKey(key)) {
				throw new IllegalArgumentException(
						"Script option defined multiple times: " + key + " for " + lastConfig.files);
			}
			lastConfig.options.put(key, value);
		}

		private void requireLastConfig() {
			if (lastConfig == null) {
				throw new IllegalArgumentException("No applicable script configuration, arguments out of order.");
			}
		}
	}
}