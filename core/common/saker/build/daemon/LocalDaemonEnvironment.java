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
package saker.build.daemon;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.EnvironmentParameters;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.FileMirrorHandler;
import saker.build.runtime.execution.InternalExecutionContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.runtime.project.SakerExecutionCache;
import saker.build.runtime.project.SakerProjectCache;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.task.TaskInvoker;
import saker.build.task.cluster.ClusterTaskInvoker;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.task.cluster.TaskInvokerInformation;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIOptions;
import saker.build.thirdparty.saker.rmi.connection.RMIServer;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.cache.CacheKey;

public class LocalDaemonEnvironment implements DaemonEnvironment {
	//TODO implement auto shutdown

	public static final String RMI_CONTEXT_VARIABLE_DAEMON_ENVIRONMENT_INSTANCE = "saker.daemon.environment.instance";
	public static final String RMI_CONTEXT_VARIABLE_DAEMON_CLUSTER_INVOKER_FACTORY = "saker.daemon.cluster.invoker.factory";

	private static final String DAEMON_LOCK_FILE_NAME = ".lock.daemon";
	private static final int STATE_UNSTARTED = 0;
	private static final int STATE_STARTED = 1;
	private static final int STATE_CLOSED = 2;

	//4 byte port 
	private static final long FILE_LOCK_DATA_LENGTH = 4;

	protected SakerEnvironmentImpl environment;
	private BuildExecutionInvoker buildExecutionInvoker;

	protected final Path sakerJarPath;

	private RandomAccessFile lockFile;
	private FileLock fileLock;

	private SocketFactory connectionSocketFactory;
	private ServerSocketFactory serverSocketFactory;

	private volatile int state = 0;

	private DaemonLaunchParameters launchParameters;
	protected final DaemonLaunchParameters constructLaunchParameters;

	private DaemonOutputController outputController;

	private RMIServer server;
	private RMIOptions connectionBaseRMIOptions;
	private ThreadGroup serverThreadGroup;

	public LocalDaemonEnvironment(Path sakerJarPath, DaemonLaunchParameters launchParameters,
			DaemonOutputController outputController, SocketFactory connectionsocketfactory) {
		Objects.requireNonNull(sakerJarPath, "sakerJarPath");
		this.sakerJarPath = sakerJarPath;
		this.outputController = outputController;
		this.constructLaunchParameters = launchParameters;
		this.connectionSocketFactory = connectionsocketfactory;
	}

	public LocalDaemonEnvironment(Path sakerJarPath, DaemonLaunchParameters launchParameters,
			DaemonOutputController outputController) {
		this(sakerJarPath, launchParameters, outputController, SocketFactory.getDefault());
	}

	public void setConnectionBaseRMIOptions(RMIOptions connectionBaseRMIOptions) {
		checkUnstarted();
		this.connectionBaseRMIOptions = connectionBaseRMIOptions == null ? null
				: new RMIOptions(connectionBaseRMIOptions);
	}

	public void setServerThreadGroup(ThreadGroup serverThreadGroup) {
		checkUnstarted();
		this.serverThreadGroup = serverThreadGroup;
	}

	public void setConnectionSocketFactory(SocketFactory connectionSocketFactory) {
		checkUnstarted();
		this.connectionSocketFactory = connectionSocketFactory;
	}

	public void setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
		checkUnstarted();
		this.serverSocketFactory = serverSocketFactory;
	}

	@Override
	public DaemonOutputController getOutputController() {
		return outputController;
	}

	@Override
	public DaemonLaunchParameters getLaunchParameters() {
		return constructLaunchParameters;
	}

	@Override
	public DaemonLaunchParameters getRuntimeLaunchConfiguration() {
		checkStarted();
		return launchParameters;
	}

	public SocketAddress getServerSocketAddress() {
		checkStarted();
		if (server == null) {
			return null;
		}
		return server.getLocalSocketAddress();
	}

	public SakerEnvironmentImpl getSakerEnvironment() {
		return environment;
	}

	@Override
	public BuildExecutionInvoker getExecutionInvoker() {
		checkStarted();
		return buildExecutionInvoker;
	}

	@Override
	public UUID getEnvironmentIdentifier() {
		checkStarted();
		return environment.getEnvironmentIdentifier();
	}

	@SuppressWarnings("try")
	public synchronized final void start() throws FileNotFoundException, IOException {
		checkUnstarted();

		Path storagedirectory = LocalFileProvider.toRealPath(constructLaunchParameters.getStorageDirectory());
		EnvironmentParameters params = EnvironmentParameters.builder(sakerJarPath).setStorageDirectory(storagedirectory)
				.setUserParameters(constructLaunchParameters.getUserParameters())
				.setThreadFactor(constructLaunchParameters.getThreadFactor()).build();

		Path lockpath = storagedirectory.resolve(DAEMON_LOCK_FILE_NAME);
		LocalFileProvider.getInstance().createDirectories(storagedirectory);

		DaemonLaunchParameters.Builder builder = DaemonLaunchParameters.builder(constructLaunchParameters);
		builder.setPort(-1);

		Integer serverport = constructLaunchParameters.getPort();
		if (serverport != null) {
			ServerSocketFactory socketFactory = serverSocketFactory;
			InetAddress serverBindAddress;
			if (constructLaunchParameters.isActsAsServer()) {
				serverBindAddress = null;
			} else {
				serverBindAddress = InetAddress.getLoopbackAddress();
			}

			int useport = serverport < 0 ? DaemonLaunchParameters.DEFAULT_PORT : serverport;

			boolean actsascluster = constructLaunchParameters.isActsAsCluster();

			//TODO allow running multiple daemons with shared storage directory

			lockFile = new RandomAccessFile(lockpath.toFile(), "rw");
			FileChannel channel = lockFile.getChannel();
			try (FileLock portlock = channel.lock(0, FILE_LOCK_DATA_LENGTH, false)) {
				fileLock = channel.tryLock(FILE_LOCK_DATA_LENGTH, Long.MAX_VALUE - FILE_LOCK_DATA_LENGTH, false);
				if (fileLock == null) {
					int port = lockFile.readInt();
					IOUtils.closePrint(lockFile);
					lockFile = null;
					throw new IOException("Failed to create daemon environment. " + "Failed to lock storage directory: "
							+ storagedirectory + ". " + "Daemon is already running on port: " + port);
				}

				environment = createSakerEnvironment(params);
				buildExecutionInvoker = new EnvironmentBuildExecutionInvoker(environment);
				server = new RMIServer(socketFactory, useport, serverBindAddress) {
					@Override
					protected RMIOptions getRMIOptionsForAcceptedConnection(Socket acceptedsocket,
							int protocolversion) {
						RMIOptions options;
						if (connectionBaseRMIOptions == null) {
							options = new RMIOptions().classResolver(new ClassLoaderResolverRegistry(
									RemoteDaemonConnection.createConnectionBaseClassLoaderResolver()));
						} else {
							options = connectionBaseRMIOptions;
						}
						return options;
					}

					@Override
					protected void setupConnection(Socket acceptedsocket, RMIConnection connection) throws IOException {
						connection.putContextVariable(RMI_CONTEXT_VARIABLE_DAEMON_ENVIRONMENT_INSTANCE,
								LocalDaemonEnvironment.this);
						if (actsascluster) {
							ClassLoaderResolverRegistry connectionclresolver = (ClassLoaderResolverRegistry) connection
									.getClassLoaderResolver();
							connection.putContextVariable(RMI_CONTEXT_VARIABLE_DAEMON_CLUSTER_INVOKER_FACTORY,
									new LocalDaemonClusterInvokerFactory(connectionclresolver,
											constructLaunchParameters.getClusterMirrorDirectory()));
						}
						super.setupConnection(acceptedsocket, connection);
					}
				};
				server.start(serverThreadGroup);
				builder.setPort(server.getPort());
			}
			int port = server.getPort();
			lockFile.writeInt(port);
		} else {
			environment = createSakerEnvironment(params);
			buildExecutionInvoker = new EnvironmentBuildExecutionInvoker(environment);
		}
		builder.setStorageDirectory(SakerPath.valueOf(environment.getStorageDirectoryPath()));
		builder.setThreadFactor(environment.getThreadFactor());

		launchParameters = builder.build();

		state = STATE_STARTED;
	}

	protected static String createClusterTaskInvokerRMIRegistryClassResolverId(ExecutionPathConfiguration pathconfig) {
		ProviderHolderPathKey workingdirpathkey = pathconfig.getWorkingDirectoryPathKey();
		String resolverid = "execclasses:" + workingdirpathkey.getFileProviderKey().getUUID() + ":"
				+ workingdirpathkey.getPath();
		return resolverid;
	}

	protected SakerEnvironmentImpl createSakerEnvironment(EnvironmentParameters params) {
		SakerEnvironmentImpl result = new SakerEnvironmentImpl(params);
		result.redirectStandardIO();
		return result;
	}

	public static Integer getRunningDaemonPort(SakerPath storagedirectory) throws IOException {
		return getRunningDaemonPort(LocalFileProvider.toRealPath(storagedirectory));
	}

	@SuppressWarnings("try")
	public static Integer getRunningDaemonPort(Path storagedirectory) throws IOException {
		Path lockpath = storagedirectory.resolve(DAEMON_LOCK_FILE_NAME);
		try (RandomAccessFile f = new RandomAccessFile(lockpath.toFile(), "r")) {
			FileChannel channel = f.getChannel();
			try (FileLock portlock = channel.lock(0, FILE_LOCK_DATA_LENGTH, true)) {
				try (FileLock mainlock = channel.tryLock(FILE_LOCK_DATA_LENGTH, Long.MAX_VALUE - FILE_LOCK_DATA_LENGTH,
						true)) {
					if (mainlock != null) {
						//if we can acquire the main lock, then there is no daemon running
						//return null to indicate that
						return null;
					}
				}
				int resport = f.readInt();
				if (resport < 0) {
					//return negative to signal that a daemon is running, but does not accept connections
					return -1;
				}
				return resport;
			}
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	@Override
	public RemoteDaemonConnection connectTo(SocketAddress address) throws IOException {
		checkStarted();
		SocketFactory connsockfactory = connectionSocketFactory;
		if (connsockfactory == null) {
			throw new IOException("Connection socket factory is not set.");
		}
		try {
			return environment.getCachedData(new RemoteConnectionCaheKey(connsockfactory, address));
		} catch (Exception e) {
			throw new IOException("Failed to connect to daemon at: " + address, e);
		}
	}

	private void checkUnstarted() {
		if (state != STATE_UNSTARTED) {
			throw new IllegalStateException("already started.");
		}
	}

	private void checkStarted() throws IllegalStateException {
		if (state != STATE_STARTED) {
			throw new IllegalStateException((state == STATE_UNSTARTED ? "Not yet started." : "Closed."));
		}
	}

	@Override
	@SuppressWarnings("try")
	public synchronized final void close() {
		if (state != STATE_STARTED) {
			return;
		}
		state = STATE_CLOSED;

		IOUtils.closePrint(server);

		IOUtils.closePrint(environment);

		FileLock fl = this.fileLock;
		RandomAccessFile lockfile = this.lockFile;
		if (fl != null) {
			try (FileLock portlock = lockfile.getChannel().lock(0, FILE_LOCK_DATA_LENGTH, false)) {
				try {
					lockfile.seek(0);
					lockfile.writeInt(-1);
				} catch (IOException e) {
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				fl.release();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		IOUtils.closePrint(fl, lockfile);
	}

	@Override
	public ProjectCacheHandle getProject(PathKey workingdir) throws IOException {
		return getProjectImpl(workingdir);
	}

	private DaemonProjectHandle getProjectImpl(PathKey workingdir) throws IOException {
		checkStarted();
		try {
			return environment.getCachedData(new ProjectCacheKey(environment, workingdir));
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private static final class RemoteConnectionCaheKey
			implements CacheKey<CloseProtectedRemoteDaemonConnection, RemoteDaemonConnection> {

		private SocketFactory socketFactory;
		private SocketAddress address;

		public RemoteConnectionCaheKey(SocketFactory socketFactory, SocketAddress address) {
			this.socketFactory = socketFactory;
			this.address = address;
		}

		@Override
		public RemoteDaemonConnection allocate() throws Exception {
			return RemoteDaemonConnection.connect(socketFactory, address);
		}

		@Override
		public CloseProtectedRemoteDaemonConnection generate(RemoteDaemonConnection resource) throws Exception {
			return new CloseProtectedRemoteDaemonConnection(resource);
		}

		@Override
		public boolean validate(CloseProtectedRemoteDaemonConnection data, RemoteDaemonConnection resource) {
			boolean result = resource.isConnected();
			if (!result) {
				try {
					resource.close();
				} catch (IOException e) {
					// XXX handle remote connection closing failure exception?
					e.printStackTrace();
				}
			}
			return result;
		}

		@Override
		public long getExpiry() {
			//5 min linger for the connection
			return 5 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(CloseProtectedRemoteDaemonConnection data, RemoteDaemonConnection resource) throws Exception {
			resource.close();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((address == null) ? 0 : address.hashCode());
			result = prime * result + System.identityHashCode(socketFactory);
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
			RemoteConnectionCaheKey other = (RemoteConnectionCaheKey) obj;
			if (address == null) {
				if (other.address != null)
					return false;
			} else if (!address.equals(other.address))
				return false;
			if (this.socketFactory != other.socketFactory) {
				return false;
			}
			return true;
		}

	}

	private static final class DaemonProjectHandle implements ProjectCacheHandle {
		protected final SakerProjectCache project;

		private DaemonProjectHandle(SakerProjectCache resource) {
			this.project = resource;
		}

		@Override
		public void clean() {
			project.clean();
		}

		@Override
		public void close() throws IOException {
			project.close();
		}

		@Override
		public SakerProjectCache toProject() {
			return project;
		}

		@Override
		public void reset() {
			project.reset();
		}
	}

	private static class ProjectCacheKey implements CacheKey<DaemonProjectHandle, SakerProjectCache> {
		private final transient SakerEnvironmentImpl environment;
		private final PathKey workingDirectory;

		public ProjectCacheKey(SakerEnvironmentImpl environment, PathKey workingDirectory) {
			this.environment = environment;
			this.workingDirectory = workingDirectory;
		}

		@Override
		public SakerProjectCache allocate() throws Exception {
			return new SakerProjectCache(environment);
		}

		@Override
		public DaemonProjectHandle generate(SakerProjectCache resource) throws Exception {
			return new DaemonProjectHandle(resource);
		}

		@Override
		public boolean validate(DaemonProjectHandle data, SakerProjectCache resource) {
			return !resource.isClosed();
		}

		@Override
		public long getExpiry() {
			return 15 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(DaemonProjectHandle data, SakerProjectCache resource) throws Exception {
			resource.close();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getWorkingDirectory().hashCode();
			return result;
		}

		public SakerPath getWorkingDirectory() {
			return workingDirectory.getPath();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ProjectCacheKey other = (ProjectCacheKey) obj;
			if (!getWorkingDirectory().equals(other.getWorkingDirectory())) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + workingDirectory + "]";
		}

	}

	private class LocalDaemonClusterInvokerFactory implements TaskInvokerFactory {
		private final Path clusterMirrorDirectory;
		private final ClassLoaderResolverRegistry connectionClassLoaderRegistry;

		public LocalDaemonClusterInvokerFactory(ClassLoaderResolverRegistry connectionclregistry,
				SakerPath clusterMirrorDirectory) {
			this.connectionClassLoaderRegistry = connectionclregistry;
			this.clusterMirrorDirectory = clusterMirrorDirectory == null ? null
					: LocalFileProvider.toRealPath(clusterMirrorDirectory);
		}

		@Override
		public TaskInvoker createTaskInvoker(ExecutionContext executioncontext,
				TaskInvokerInformation invokerinformation) throws IOException, NullPointerException {
			try {
				ExecutionPathConfiguration realpathconfig = executioncontext.getPathConfiguration();
				ProviderHolderPathKey workingpathkey = realpathconfig.getWorkingDirectoryPathKey();
				SakerProjectCache project = getProjectImpl(workingpathkey).project;
				Path modifiedmirrordir;
				if (clusterMirrorDirectory == null) {
					modifiedmirrordir = null;
				} else {
					//XXX maybe put a notice text file about the origin of the directory
					modifiedmirrordir = getMirrorDirectoryPathForWorkingDirectory(clusterMirrorDirectory,
							workingpathkey);
				}

				return new TaskInvoker() {
					@Override
					public void run(TaskInvocationContext context) throws Exception {
						((InternalExecutionContext) executioncontext).internalGetBuildTrace()
								.startBuildCluster(environment);
						try {
							project.clusterStarting(realpathconfig, executioncontext.getRepositoryConfiguration(),
									executioncontext.getScriptConfiguration(), executioncontext.getUserParameters(),
									modifiedmirrordir, invokerinformation.getCoordinatorProviderKey(),
									invokerinformation.getDatabaseConfiguration(), executioncontext);
						} catch (Exception e) {
							//XXX reify exception
							throw new IOException(e);
						}

						SakerExecutionCache execcache = project.getExecutionCache();

						String resolverid = createClusterTaskInvokerRMIRegistryClassResolverId(realpathconfig);
						ClassLoaderResolver execresolver = execcache.getExecutionClassLoaderResolver();

						FileMirrorHandler mirrorhandler = project.getClusterMirrorHandler();
						SakerEnvironment executionenvironment = project.getExecutionCache().getRecordingEnvironment();

						Object execkey = environment.getStartExecutionKey();
						try {
							connectionClassLoaderRegistry.register(resolverid, execresolver);
							new ClusterTaskInvoker(environment, executionenvironment, executioncontext, mirrorhandler,
									execcache.getLoadedBuildRepositories(), project.getClusterContentDatabase(),
									execcache.getLoadedScriptProviderLocators()).run(context);
						} finally {
							connectionClassLoaderRegistry.unregister(resolverid, execresolver);
							project.clusterFinished(execkey);
						}

					}
				};
			} catch (Throwable e) {
				e.printStackTrace();
				throw e;
			}
		}

		@Override
		public UUID getEnvironmentIdentifier() {
			return environment.getEnvironmentIdentifier();
		}

	}

	private static class CloseProtectedRemoteDaemonConnection implements RemoteDaemonConnection {
		private final RemoteDaemonConnection subject;

		public CloseProtectedRemoteDaemonConnection(RemoteDaemonConnection subject) {
			this.subject = subject;
		}

		@Override
		public DaemonEnvironment getDaemonEnvironment() {
			return subject.getDaemonEnvironment();
		}

		@Override
		public boolean isConnected() {
			return subject.isConnected();
		}

		@Override
		public SocketAddress getAddress() {
			return subject.getAddress();
		}

		@Override
		public void addConnectionIOErrorListener(ConnectionIOErrorListener listener) {
			subject.addConnectionIOErrorListener(listener);
		}

		@Override
		public TaskInvokerFactory getClusterTaskInvokerFactory() {
			//TODO we probably need to adjust the incoming rmi connection class resolver registry. Should we disable multi-hop rmi cluster usage?
			return subject.getClusterTaskInvokerFactory();
		}

		@Override
		public void close() {
			//do not close subject
		}
	}

	public static Path getMirrorDirectoryPathForWorkingDirectory(Path basemirrordir, PathKey workingpathkey) {
		byte[] hash = FileUtils
				.hashString(workingpathkey.getFileProviderKey().getUUID() + "/" + workingpathkey.getPath());
		return basemirrordir.resolve(StringUtils.toHexString(hash));
	}
}
