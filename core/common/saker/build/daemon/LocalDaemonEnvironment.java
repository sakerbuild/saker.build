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

import java.io.DataOutput;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.Lock;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

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
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.InvalidBuildConfigurationException;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.runtime.project.SakerExecutionCache;
import saker.build.runtime.project.SakerProjectCache;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.task.cluster.ClusterTaskInvoker;
import saker.build.task.cluster.TaskInvoker;
import saker.build.task.cluster.TaskInvokerRMIWrapper;
import saker.build.task.cluster.TaskInvokerInformation;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIOptions;
import saker.build.thirdparty.saker.rmi.connection.RMIServer;
import saker.build.thirdparty.saker.rmi.connection.RMISocketConfiguration;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.rmi.exception.RMIContextVariableNotFoundException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.trace.InternalBuildTrace;
import saker.build.util.cache.CacheKey;
import saker.build.util.exc.ExceptionView;
import saker.build.util.rmi.SakerRMIHelper;
import testing.saker.build.flag.TestFlag;

public class LocalDaemonEnvironment implements DaemonEnvironment {
	//TODO implement auto shutdown

	/**
	 * {@link DaemonAccess}
	 */
	public static final String RMI_CONTEXT_VARIABLE_DAEMON_ACCESS = "saker.daemon.access";

	public static final String RMI_CONTEXT_VARIABLE_CLUSTER_INVOKER = "saker.daemon.cluster.invoker";

	public static final String DAEMON_LOCK_FILE_NAME = ".lock.daemon";

	private static final int STATE_UNSTARTED = 0;
	private static final int STATE_INITIALIZED = 1;
	private static final int STATE_STARTED = 2;
	private static final int STATE_CLOSED = 3;
	private static final AtomicIntegerFieldUpdater<LocalDaemonEnvironment> AIFU_state = AtomicIntegerFieldUpdater
			.newUpdater(LocalDaemonEnvironment.class, "state");

	private static final Method METHOD_TASK_INVOKER_FACTORY_RUN = ReflectUtils.getMethodAssert(TaskInvoker.class, "run",
			ExecutionContext.class, TaskInvokerInformation.class, TaskInvocationContext.class);

	/**
	 * Format:
	 * 
	 * <pre>
	 * no data
	 * </pre>
	 */
	private static final int DAEMON_CONNECTION_FORMAT_TCP = 1;
	/**
	 * Format:
	 * 
	 * <pre>
	 * 4 byte keystore utf8 byte count
	 * keystore utf8 full absolute path bytes
	 * </pre>
	 */
	private static final int DAEMON_CONNECTION_FORMAT_SSL = 2;

	/**
	 * <pre>
	 * 4 byte format
	 * 4 byte port
	 * </pre>
	 */
	private static final long FILE_LOCK_DATA_LENGTH = 4096;
	//can't run more than 65535 daemons as we would run out of ports
	private static final int DAEMON_INSTANCE_INDEX_END = 0xFFFF;

	private static final long FILE_LOCK_REGION_START = Long.MAX_VALUE / 2;
	private static final long FILE_LOCK_REGION_END = FILE_LOCK_REGION_START
			+ FILE_LOCK_DATA_LENGTH * DAEMON_INSTANCE_INDEX_END;
	private static final long FILE_LOCK_REGION_LENGTH = FILE_LOCK_REGION_END - FILE_LOCK_REGION_START;

	private static final RMIOptions CONNECTION_BASE_RMI_OPTIONS = SakerRMIHelper.createBaseRMIOptions().classResolver(
			new ClassLoaderResolverRegistry(RemoteDaemonConnection.createConnectionBaseClassLoaderResolver()));

	protected SakerEnvironmentImpl environment;
	private BuildExecutionInvoker buildExecutionInvoker;

	protected final Path sakerJarPath;

	private RandomAccessFile lockFile;
	private FileLock fileLock;

	private SocketFactory connectionSocketFactory;
	private ServerSocketFactory serverSocketFactory;

	private SakerPath sslKeystorePath;

	private volatile int state = STATE_UNSTARTED;

	private DaemonLaunchParameters launchParameters;
	protected final DaemonLaunchParameters constructLaunchParameters;

	private final DaemonOutputController outputController;

	private RMIServer server;
	private RMIOptions connectionBaseRMIOptions = CONNECTION_BASE_RMI_OPTIONS;
	private ThreadGroup serverThreadGroup;

	/**
	 * Synchronized collection.
	 */
	private Set<WeakReference<TaskInvoker>> clientClusterTaskInvokers = Collections.synchronizedSet(new HashSet<>());
	private Set<? extends AddressResolver> connectToAsClusterAddresses;
	private ThreadWorkPool clusterClientConnectingWorkPool;
	private ThreadGroup clusterClientConnectingThreadGroup;

	//may be a weak set as the keys stay referenced by the cache 
	/**
	 * Locked by {@link #projectCacheKeysLock}.
	 */
	private final Set<ProjectCacheKey> loadedProjectCacheKeys = Collections.newSetFromMap(new WeakHashMap<>());

	private final Lock projectCacheKeysLock = ThreadUtils.newExclusiveLock();
	private final Lock stateLock = ThreadUtils.newExclusiveLock();

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
		this(sakerJarPath, launchParameters, outputController, null);
	}

	public LocalDaemonEnvironment(Path sakerJarPath, DaemonLaunchParameters launchParameters) {
		this(sakerJarPath, launchParameters, null, null);
	}

	public void setConnectionBaseRMIOptions(RMIOptions connectionBaseRMIOptions) {
		checkUnstarted();
		this.connectionBaseRMIOptions = connectionBaseRMIOptions == null ? CONNECTION_BASE_RMI_OPTIONS
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

	public void setSslKeystorePath(SakerPath sslKeystorePath) {
		this.sslKeystorePath = sslKeystorePath;
	}

	public void setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
		checkUnstarted();
		this.serverSocketFactory = serverSocketFactory;
	}

	public void setConnectToAsClusterAddresses(Set<? extends AddressResolver> serveraddresses) {
		if (!constructLaunchParameters.isActsAsCluster()) {
			throw new IllegalArgumentException("Daemon environment doesn't act as cluster.");
		}
		checkUnstarted();
		this.connectToAsClusterAddresses = serveraddresses;
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
		checkStartedOrInitialized();
		return launchParameters;
	}

	public SocketAddress getServerSocketAddress() {
		checkStartedOrInitialized();
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
		checkStartedOrInitialized();
		return environment.getEnvironmentIdentifier();
	}

	public final void init() throws IOException {
		final Lock lock = stateLock;
		IOUtils.lockIO(lock, "Initialization interrupted.");
		try {
			initLocked();
		} finally {
			lock.unlock();
		}
	}

	@SuppressWarnings("try") // unused FileLock in try
	private void initLocked() throws IOException {
		checkUnstarted();

		try {
			Path storagedirectory = LocalFileProvider.toRealPath(constructLaunchParameters.getStorageDirectory());
			EnvironmentParameters params = EnvironmentParameters.builder(sakerJarPath)
					.setStorageDirectory(storagedirectory)
					.setUserParameters(constructLaunchParameters.getUserParameters())
					.setThreadFactor(constructLaunchParameters.getThreadFactor()).build();

			Path lockpath = storagedirectory.resolve(DAEMON_LOCK_FILE_NAME);
			LocalFileProvider.getInstance().createDirectories(storagedirectory);

			DaemonLaunchParameters.Builder builder = DaemonLaunchParameters.builder(constructLaunchParameters);

			Integer serverport = constructLaunchParameters.getPort();
			if (serverport != null) {
				InetAddress serverBindAddress;
				if (constructLaunchParameters.isActsAsServer()) {
					serverBindAddress = null;
				} else {
					serverBindAddress = InetAddress.getLoopbackAddress();
				}

				int useport = serverport < 0 ? DaemonLaunchParameters.DEFAULT_PORT : serverport;

				boolean actsascluster = constructLaunchParameters.isActsAsCluster();

				int daemonInstanceIndex = -1;

				lockFile = new RandomAccessFile(lockpath.toFile(), "rw");
				FileChannel channel = lockFile.getChannel();
				for (int i = 0; i < DAEMON_INSTANCE_INDEX_END; i++) {
					FileLock flock = channel.tryLock(FILE_LOCK_REGION_START + i * FILE_LOCK_DATA_LENGTH,
							FILE_LOCK_DATA_LENGTH, false);
					if (flock == null) {
						continue;
					}
					daemonInstanceIndex = i;
					fileLock = flock;
					break;
				}
				if (fileLock == null) {
					throw new IOException("Failed to start daemon server, unable to acquire lock. Already running "
							+ DAEMON_INSTANCE_INDEX_END + " daemons?");
				}

				//lock the data as well to lock initialization 
				try (FileLock datalock = channel.lock(FILE_LOCK_DATA_LENGTH * daemonInstanceIndex,
						FILE_LOCK_DATA_LENGTH, false)) {
					environment = createSakerEnvironment(params);

					server = new DaemonRMIServer(serverSocketFactory, useport, serverBindAddress, actsascluster);
					writeDaemonInformationToLockFile(lockFile, daemonInstanceIndex, server.getPort(), sslKeystorePath);
				}
			} else {
				environment = createSakerEnvironment(params);
			}
			buildExecutionInvoker = new EnvironmentBuildExecutionInvoker(environment);

			builder.setStorageDirectory(SakerPath.valueOf(environment.getStorageDirectoryPath()));
			builder.setThreadFactor(environment.getThreadFactor());
			builder.setPort(server == null ? null : server.getPort());
			launchParameters = builder.build();

			state = STATE_INITIALIZED;
		} catch (Throwable e) {
			try {
				IOUtils.addExc(e, IOUtils.closeExc(server, environment, fileLock, lockFile));
				server = null;
				environment = null;
				fileLock = null;
				lockFile = null;
				buildExecutionInvoker = null;
				launchParameters = null;

				clusterClientConnectingThreadGroup = null;
				if (clusterClientConnectingWorkPool != null) {
					clusterClientConnectingWorkPool.exit();
					clusterClientConnectingWorkPool = null;
				}
				state = STATE_UNSTARTED;
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
	}

	private static void writeDaemonInformationToLockFile(RandomAccessFile lockfile, int daemonInstanceIndex, int port,
			SakerPath sslpath) throws IOException {
		lockfile.seek(daemonInstanceIndex * FILE_LOCK_DATA_LENGTH);
		writeDaemonInformationToDataOutput(lockfile, port, sslpath);
	}

	private static void writeDaemonInformationToDataOutput(DataOutput output, int port, SakerPath sslpath)
			throws IOException {
		if (sslpath != null) {
			byte[] bytes = sslpath.toString().getBytes(StandardCharsets.UTF_8);
			output.writeInt(DAEMON_CONNECTION_FORMAT_SSL);
			output.writeInt(port);
			output.writeInt(bytes.length);
			output.write(bytes);
		} else {
			output.writeInt(DAEMON_CONNECTION_FORMAT_TCP);
			output.writeInt(port);
		}
	}

	/**
	 * Runs the daemon.
	 * <p>
	 * Starts connections to other clusters, and if the daemon was configured to act as a server, it will start to
	 * accept the connections on the current thread.
	 * <p>
	 * If the daemon doesn't run as a server, then this method returns, and will continue to run separately.
	 */
	public final void run() {
		if (!AIFU_state.compareAndSet(this, STATE_INITIALIZED, STATE_STARTED)) {
			throw new IllegalStateException("Daemon environment is not in initialized state.");
		}
		startConnectionsToClusters();
		RMIServer server = this.server;
		if (server != null) {
			server.acceptConnections(serverThreadGroup);
		}
	}

	public final void startAfterInitialization() {
		if (!AIFU_state.compareAndSet(this, STATE_INITIALIZED, STATE_STARTED)) {
			throw new IllegalStateException("Daemon environment is not in initialized state.");
		}
		startConnectionsToClusters();
		RMIServer server = this.server;
		if (server != null) {
			server.start(serverThreadGroup);
		}
	}

	/**
	 * Same as calling {@link #init()} and {@link #startAfterInitialization()} after each other.
	 */
	public final void start() throws IOException {
		init();
		startAfterInitialization();
	}

	private void startConnectionsToClusters() {
		if (ObjectUtils.isNullOrEmpty(connectToAsClusterAddresses)) {
			return;
		}
		if (serverThreadGroup != null) {
			clusterClientConnectingThreadGroup = new ThreadGroup(serverThreadGroup, "Daemon client connector");
		} else {
			clusterClientConnectingThreadGroup = new ThreadGroup("Daemon client connector");
		}
		clusterClientConnectingWorkPool = ThreadUtils.newDynamicWorkPool(clusterClientConnectingThreadGroup,
				"cluster-client-");
		for (AddressResolver addr : connectToAsClusterAddresses) {
			clusterClientConnectingWorkPool.offer(new ClusterClientConnectingRunnable(addr));
		}
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

	public static List<RunningDaemonConnectionInfo> getRunningDaemonInfos(SakerPath storagedirectory)
			throws IOException {
		return getRunningDaemonInfos(LocalFileProvider.toRealPath(storagedirectory));
	}

	public static List<RunningDaemonConnectionInfo> getRunningDaemonInfos(Path storagedirectory) throws IOException {
		List<RunningDaemonConnectionInfo> result = new ArrayList<>();
		Path lockpath = storagedirectory.resolve(DAEMON_LOCK_FILE_NAME);
		try (RandomAccessFile f = new RandomAccessFile(lockpath.toFile(), "r")) {
			collectDaemonInfosFromLockFile(result, f);
			return result;
		} catch (FileNotFoundException e) {
			return Collections.emptyList();
		}
	}

	@SuppressWarnings("try") // unused FileLock in try
	private static void collectDaemonInfosFromLockFile(List<RunningDaemonConnectionInfo> result, RandomAccessFile f)
			throws IOException {
		//XXX there is a race condition when concurrently creating deamons
		//    in theory, if we poll the lock file with locks, and happen to lock the only
		//    free region in the file, then concurrently creating daemons will not be possible
		//    as they won't find a free slot.
		//    this is such an edge case that currently its infeasible to support.
		//    easy solution is not to create more than like 10000 daemons on a single machine.
		FileChannel channel = f.getChannel();
		for (int i = 0; i < DAEMON_INSTANCE_INDEX_END; i++) {
			FileLock flock = channel.tryLock(FILE_LOCK_REGION_START + i * FILE_LOCK_DATA_LENGTH, FILE_LOCK_DATA_LENGTH,
					true);
			try {
				if (flock != null) {
					//close the lock before attempting the remaining
					IOUtils.close(flock);
					flock = null;
					int remainingregioncount = DAEMON_INSTANCE_INDEX_END - i - 1;
					if (remainingregioncount <= 1) {
						//don't perform explicit check of the next one, its done as usual in the next loop
						continue;
					}

					//locked the region of a daemon
					//no daemon is running here
					//test if there are any more in the file
					//do the test in 2 phases not to lock the whole region at once
					//as that could prevent concurrent daemons of starting
					long remlockstart = FILE_LOCK_REGION_START + (i + 1) * FILE_LOCK_DATA_LENGTH;
					long remlocktotallen = remainingregioncount * FILE_LOCK_DATA_LENGTH;
					int remregion1count = remainingregioncount / 2;
					long remlocklen1 = remregion1count * FILE_LOCK_DATA_LENGTH;
					long remlocklen2 = remlocktotallen - remlocklen1;
					long remlockpos1 = remlockstart;
					long remlockpos2 = remlockstart + remlocklen1;
					//lock the later region first as if we lock in order
					//then in rare cases a concurrent daemon may fail to start
					boolean nonerunningin1 = true;
					boolean nonerunningin2 = true;
					try (FileLock remlock = channel.tryLock(remlockpos2, remlocklen2, true)) {
						if (remlock == null) {
							//some region is locked by other
							nonerunningin2 = false;
						}
					}
					try (FileLock remlock = channel.tryLock(remlockpos1, remlocklen1, true)) {
						if (remlock == null) {
							//some region is locked by other
							nonerunningin1 = false;
						}
					}
					if (!nonerunningin1) {
						//theres a daemon running in the first region, continue
						continue;
					}
					//no locks in the first region, we can skip over that
					i += remregion1count;
					if (nonerunningin2) {
						//both regions succeeded to lock
						//no more daemons
						return;
					}
					//continue checking
					continue;
				}
			} finally {
				IOUtils.close(flock);
			}
			//lock on the data to wait initialization
			try (FileLock datalock = channel.lock(FILE_LOCK_DATA_LENGTH * i, FILE_LOCK_DATA_LENGTH, true)) {
				f.seek(i * FILE_LOCK_DATA_LENGTH);
				int format = f.readInt();
				int resport = f.readInt();
				if (resport < 0) {
					continue;
				}
				switch (format) {
					case DAEMON_CONNECTION_FORMAT_TCP: {
						result.add(RunningDaemonConnectionInfo.createTCP(resport));
						break;
					}
					case DAEMON_CONNECTION_FORMAT_SSL: {
						int utf8len = f.readInt();
						if (utf8len > FILE_LOCK_DATA_LENGTH) {
							//invalid
							continue;
						}
						try {
							String kspathstr;
							if (utf8len > 0) {
								byte[] keystoreutf8bytes = new byte[utf8len];
								f.readFully(keystoreutf8bytes);
								kspathstr = new String(keystoreutf8bytes, StandardCharsets.UTF_8);
							} else {
								kspathstr = null;
							}
							RunningDaemonConnectionInfo info = RunningDaemonConnectionInfo.createSSL(resport,
									kspathstr);
							result.add(info);
						} catch (RuntimeException e) {
							//some decoding exception or other things. don't throw, can be silently ignored
							if (TestFlag.ENABLED) {
								throw e;
							}
						}
						break;
					}
					default: {
						break;
					}
				}
			}
		}
	}

	@Override
	public RemoteDaemonConnection connectTo(SocketAddress address) throws IOException {
		checkStarted();
		SocketFactory connsockfactory = connectionSocketFactory;
		try {
			return environment.getCachedData(new RemoteConnectionCaheKey(connsockfactory, address));
		} catch (Exception e) {
			throw new IOException("Failed to connect to daemon at: " + address, e);
		}
	}

	@Override
	public Collection<? extends TaskInvoker> getClientClusterTaskInvokers() {
		Collection<TaskInvoker> result = new ArrayList<>();
		clientClusterTaskInvokers.forEach(r -> {
			TaskInvoker tif = r.get();
			if (tif == null) {
				return;
			}
			result.add(tif);
		});
		return result;
	}

	private void checkUnstarted() {
		if (state != STATE_UNSTARTED) {
			throw new IllegalStateException("already started.");
		}
	}

	private void checkStarted() throws IllegalStateException {
		int state = this.state;
		if (state != STATE_STARTED) {
			throw new IllegalStateException((state == STATE_UNSTARTED ? "Not yet started." : "Closed."));
		}
	}

	private void checkStartedOrInitialized() throws IllegalStateException {
		int state = this.state;
		if (state != STATE_STARTED && state != STATE_INITIALIZED) {
			throw new IllegalStateException((state == STATE_UNSTARTED ? "Not yet started/initialized." : "Closed."));
		}
	}

	/**
	 * Subclasses can override.
	 */
	protected void closeImpl() {
	}

	@Override
	public final void close() {
		final Lock lock = stateLock;
		lock.lock();
		try {
			closeLocked();
		} finally {
			lock.unlock();
		}
	}

	private void closeLocked() {
		boolean interrupted = false;
		try {
			state = STATE_CLOSED;

			ThreadGroup connthreadgroup = clusterClientConnectingThreadGroup;
			if (connthreadgroup != null) {
				connthreadgroup.interrupt();
				//signal exit to the thread pool, but don't wait for it yet.
				try {
					clusterClientConnectingWorkPool.exit();
				} catch (Exception e) {
					//shouldn't happen but print anyway
					e.printStackTrace();
				}
			}
			Collection<ProjectCacheKey> keys;
			projectCacheKeysLock.lock();
			try {
				keys = new HashSet<>(loadedProjectCacheKeys);
				loadedProjectCacheKeys.clear();
			} finally {
				projectCacheKeysLock.unlock();
			}
			SakerEnvironmentImpl environment = this.environment;
			if (environment != null) {
				while (true) {
					try {
						environment.invalidateCachedDatasWaitExecutions(keys::contains);
						break;
					} catch (InterruptedException e) {
						//reinterrupt at end
						interrupted = true;
						//try again
						continue;
					}
				}

				//close the environment first so builds finish
				IOUtils.closePrint(environment);
			}

			RMIServer server = this.server;
			//the following resources should be closed on a background daemon thread
			//as this method can be called through RMI,
			//we need to perform this on a different thread to make sure that the response
			//of the close() method arrives to the caller.
			ThreadUtils.startDaemonThread(() -> {
				if (server != null) {
					try {
						server.closeWait();
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					} catch (Throwable e) {
						//print any exception
						try {
							closeImpl();
						} catch (Throwable e2) {
							e.addSuppressed(e2);
						}
						e.printStackTrace();
						throw e;
					}
				}

				closeImpl();
			});

			IOUtils.closePrint(this.fileLock, this.lockFile);
			this.fileLock = null;
			this.lockFile = null;

			if (connthreadgroup != null) {
				//wait for the thread pool
				try {
					clusterClientConnectingWorkPool.close();
				} catch (Exception e) {
					//shouldn't happen but print anyway
					e.printStackTrace();
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public ProjectCacheHandle getProject(PathKey workingdir) throws IOException {
		return getProjectImpl(workingdir);
	}

	static DaemonAccess getDaemonAccessContextVariable(RMIVariables vars) {
		try {
			return (DaemonAccess) vars.invokeContextVariableMethod(RMI_CONTEXT_VARIABLE_DAEMON_ACCESS,
					InternalDaemonAccess.METHOD_GETDAEMONACCESS);
		} catch (InvocationTargetException e) {
			throw new RMIContextVariableNotFoundException(
					"Failed to get Daemon access context variable: " + RMI_CONTEXT_VARIABLE_DAEMON_ACCESS, e);
		}
	}

	static void runClusterInvokerContextVariable(RMIVariables vars, ExecutionContext executioncontext,
			TaskInvokerInformation invokerinformation, TaskInvocationContext context) throws Exception {
		try {
			vars.invokeContextVariableMethod(RMI_CONTEXT_VARIABLE_CLUSTER_INVOKER, METHOD_TASK_INVOKER_FACTORY_RUN,
					executioncontext, invokerinformation, context);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			//serious error, throw it directly
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw e;
		}
	}

	private interface InternalDaemonAccess {
		public static final Method METHOD_GETDAEMONACCESS = ReflectUtils.getMethodAssert(InternalDaemonAccess.class,
				"getDaemonAccess");

		@RMIWrap(DaemonAccessCacheRMIWrapper.class)
		public DaemonAccess getDaemonAccess();
	}

	/**
	 * {@link RMIWrapper} for {@link DaemonAccess} that transfers the relevant fields.
	 * 
	 * @see DaemonAccessImpl
	 */
	private static final class DaemonAccessCacheRMIWrapper implements RMIWrapper {
		private DaemonAccess daemonAccess;

		public DaemonAccessCacheRMIWrapper() {
		}

		public DaemonAccessCacheRMIWrapper(DaemonAccess daemonAccess) {
			this.daemonAccess = daemonAccess;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(daemonAccess.getDaemonEnvironment());
			out.writeBoolean(daemonAccess.isClusterAvailable());
			out.writeRemoteObject(daemonAccess.getDaemonClientServer());
			out.writeSerializedObject(daemonAccess.getEnvironmentIdentifier());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			DaemonEnvironment daemonEnvironment = (DaemonEnvironment) in.readObject();
			boolean clusteravailable = in.readBoolean();
			DaemonClientServer clientserver = (DaemonClientServer) in.readObject();
			UUID envid = (UUID) in.readObject();
			daemonAccess = new DaemonAccessImpl(daemonEnvironment, clientserver, clusteravailable, envid);
		}

		@Override
		public Object resolveWrapped() {
			return daemonAccess;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}

	@RMIWrap(DaemonAccessCacheRMIWrapper.class)
	private static final class DaemonAccessImpl implements DaemonAccess, InternalDaemonAccess {
		private final DaemonEnvironment daemonEnvironment;
		private final DaemonClientServer clientServer;
		private final UUID environmentIdentifier;
		private final boolean clusterAvailable;

		protected DaemonAccessImpl(DaemonEnvironment daemonEnvironment, DaemonClientServer clientserver,
				boolean clusterAvailable) {
			this(daemonEnvironment, clientserver, clusterAvailable, daemonEnvironment.getEnvironmentIdentifier());
		}

		protected DaemonAccessImpl(DaemonEnvironment daemonEnvironment, DaemonClientServer clientserver,
				boolean clusterAvailable, UUID environmentIdentifier) {
			this.daemonEnvironment = daemonEnvironment;
			this.clientServer = clientserver;
			this.clusterAvailable = clusterAvailable;
			this.environmentIdentifier = environmentIdentifier;
		}

		@Override
		public DaemonEnvironment getDaemonEnvironment() {
			return daemonEnvironment;
		}

		@Override
		public DaemonClientServer getDaemonClientServer() {
			return clientServer;
		}

		@Override
		public UUID getEnvironmentIdentifier() {
			return environmentIdentifier;
		}

		@Override
		public boolean isClusterAvailable() {
			return clusterAvailable;
		}

		@Override
		public DaemonAccess getDaemonAccess() {
			return this;
		}
	}

	private final class DaemonRMIServer extends RMIServer {
		private final boolean actsAsCluster;

		private DaemonRMIServer(ServerSocketFactory socketfactory, int port, InetAddress bindaddress,
				boolean actsascluster) throws IOException {
			super(socketfactory, port, bindaddress);
			this.actsAsCluster = actsascluster;
		}

		@Override
		protected RMIOptions getRMIOptionsForAcceptedConnection(Socket acceptedsocket, int protocolversion) {
			return connectionBaseRMIOptions;
		}

		@Override
		protected void setupConnection(Socket acceptedsocket, RMIConnection connection) throws IOException {
			connection.addCloseListener(new RMIConnection.CloseListener() {
				@Override
				public void onConnectionClosed() {
					SakerRMIHelper.dumpRMIStatistics(connection);
				}
			});
			DaemonClientServerImpl clientserver = new DaemonClientServerImpl(connection);
			connection.putContextVariable(RMI_CONTEXT_VARIABLE_DAEMON_ACCESS,
					new DaemonAccessImpl(LocalDaemonEnvironment.this, clientserver, actsAsCluster));
			if (actsAsCluster) {
				ClassLoaderResolverRegistry connectionclresolver = (ClassLoaderResolverRegistry) connection
						.getClassLoaderResolver();
				connection.putContextVariable(RMI_CONTEXT_VARIABLE_CLUSTER_INVOKER, new LocalDaemonClusterInvoker(
						connectionclresolver, constructLaunchParameters.getClusterMirrorDirectory()));
			}
			super.setupConnection(acceptedsocket, connection);
		}

		@Override
		protected void validateSocket(Socket accepted) throws IOException, RuntimeException {
			if (accepted instanceof SSLSocket) {
				SSLSocket ssls = (SSLSocket) accepted;
				SSLSession session = ssls.getSession();
				try {
					Principal localprincipal = session.getLocalPrincipal();
					Principal peerprincipal = session.getPeerPrincipal();
					String ls = System.lineSeparator();

					StringBuilder sb = new StringBuilder();
					sb.append("Connection accepted from: ");
					sb.append(accepted.getRemoteSocketAddress());
					sb.append(ls);
					sb.append("Local principal: ");
					sb.append(localprincipal);
					sb.append(ls);
					sb.append("Peer principal: ");
					sb.append(peerprincipal);
					sb.append(ls);
					System.out.print(sb.toString());
				} catch (SSLPeerUnverifiedException e) {
					String ls = System.lineSeparator();
					StringBuilder sb = new StringBuilder();
					sb.append("Connection accepted from: ");
					sb.append(accepted.getRemoteSocketAddress());
					sb.append(ls);
					sb.append("Failed to verify peer identity.");
					sb.append(ls);

					System.out.print(sb.toString());
					throw e;
				} catch (Exception e) {
					String ls = System.lineSeparator();
					StringBuilder sb = new StringBuilder();
					sb.append("Connection accepted from: ");
					sb.append(accepted.getRemoteSocketAddress());
					sb.append(ls);
					sb.append("Connection error.");
					sb.append(ls);
					SakerLog.printFormatException(ExceptionView.create(e), sb, CommonExceptionFormat.NO_TRACE);

					System.out.print(sb.toString());
					throw e;
				}

			}
			super.validateSocket(accepted);
		}
	}

	public interface AddressResolver {
		public SocketAddress getAddress() throws UnknownHostException;

		@Override
		public String toString();
	}

	private DaemonProjectHandle getProjectImpl(PathKey workingdir) throws IOException {
		checkStarted();
		try {
			ProjectCacheKey cachekey = new ProjectCacheKey(environment, workingdir);
			DaemonProjectHandle result = environment.getCachedData(cachekey);
			projectCacheKeysLock.lock();
			try {
				loadedProjectCacheKeys.add(cachekey);
			} finally {
				projectCacheKeysLock.unlock();
			}
			return result;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	public static final class RunningDaemonConnectionInfo {
		protected final int port;
		protected String sslKeystorePath;

		protected static RunningDaemonConnectionInfo createTCP(int port) {
			return new RunningDaemonConnectionInfo(port, null);
		}

		protected static RunningDaemonConnectionInfo createSSL(int port, String sslKeystorePath) {
			return new RunningDaemonConnectionInfo(port, sslKeystorePath);
		}

		protected RunningDaemonConnectionInfo(int port, String sslKeystorePath) {
			this.port = port;
			this.sslKeystorePath = sslKeystorePath;
		}

		public int getPort() {
			return port;
		}

		public String getSslKeystorePath() {
			return sslKeystorePath;
		}
	}

	private final class ClusterClientConnectingRunnable implements ThrowingRunnable {
		private final AddressResolver addrResolver;

		private ClusterClientConnectingRunnable(AddressResolver addrResolver) {
			this.addrResolver = addrResolver;
		}

		@Override
		public void run() throws Exception {
			int sleepsecs = 5;
			try {
				while (!Thread.interrupted() && state == STATE_STARTED) {
					SocketAddress addr = null;
					try {
						addr = addrResolver.getAddress();
					} catch (UnknownHostException e) {
						ExceptionView ev = ExceptionView.create(e);

						System.out.println("Failed to resolve address: " + addrResolver);
						SakerLog.printFormatException(ev, System.out, SakerLog.CommonExceptionFormat.NO_TRACE);
					} catch (Exception e) {
						e.printStackTrace(System.out);
					}
					if (addr != null) {
						try {
							System.out.println("Connecting as client to: " + addr);
							RMISocketConfiguration socketconfig = new RMISocketConfiguration();
							socketconfig.setSocketFactory(connectionSocketFactory);
							socketconfig.setConnectionInterruptible(true);
							socketconfig.setConnectionTimeout(5000);

							RMIConnection rmiconn = RemoteDaemonConnection.initiateRMIConnection(addr, socketconfig);
							System.out.println("Connected as client to: " + addr);
							sleepsecs = 1;
							RMIVariables vars = null;
							try {
								vars = rmiconn.newVariables();
								DaemonAccess access = getDaemonAccessContextVariable(vars);
								DaemonClientServer clientserver = access.getDaemonClientServer();

								boolean selfconnection = false;
								try {
									clientserver.setClientClusterAvailable(environment.getEnvironmentIdentifier());
								} catch (InvalidBuildConfigurationException e) {
									String ls = System.lineSeparator();
									StringBuilder sb = new StringBuilder();
									sb.append(
											"Invalid cluster configuration error detected while connecting to daemon:");
									sb.append(ls);
									sb.append(e);
									sb.append(ls);
									sb.append("Dropping client connection attempts for: ");
									sb.append(addr);
									sb.append(ls);
									System.err.print(sb.toString());
									selfconnection = true;
								}
								if (!selfconnection) {
									final RMIConnection frmiconn = rmiconn;
									rmiconn.addCloseListener(new RMIConnection.CloseListener() {
										@Override
										public void onConnectionClosed() {
											SakerRMIHelper.dumpRMIStatistics(frmiconn);
											if (state != STATE_STARTED) {
												return;
											}
											//restart connection
											try {
												clusterClientConnectingWorkPool
														.offer(ClusterClientConnectingRunnable.this);
											} catch (Exception e) {
												//the pool may've been closed already
												e.printStackTrace();
											}
										}
									});
									rmiconn = null;
								}
								break;
							} catch (Exception e) {
								e.printStackTrace(System.out);
							} finally {
								IOUtils.closePrint(rmiconn);
							}
						} catch (ConnectException e) {
							//connection failed.
						} catch (SocketTimeoutException e) {
						} catch (ClosedByInterruptException | InterruptedIOException e) {
							//exit gracefully
							break;
						} catch (Exception e) {
							e.printStackTrace(System.out);
						}
					}
					System.out.println(
							"Connection failed to: " + addrResolver + " Sleeping for " + sleepsecs + " seconds");
					Thread.sleep(sleepsecs * 1000);
					sleepsecs += 5;
					if (sleepsecs > 30) {
						sleepsecs = 30;
					}
				}
			} catch (InterruptedException e) {
				//exit gracefully
			}
			System.out.println("Exiting client connection thread of: " + addrResolver);
		}

	}

	private final class DaemonClientServerImpl implements DaemonClientServer {
		private final RMIConnection connection;

		private DaemonClientServerImpl(RMIConnection connection) {
			this.connection = connection;
		}

		@Override
		public void setClientClusterAvailable(UUID environmentidentifier) {
			if (LocalDaemonEnvironment.this.getEnvironmentIdentifier().equals(environmentidentifier)) {
				throw new InvalidBuildConfigurationException("Attempt to add self as build cluster.");
			}
			UUID id = UUID.randomUUID();
			System.out.println("New cluster client connection: " + id);
			//TODO handle if the connection is gracefully closed by the client (that is not caused by IO error)

			RemoteDaemonConnectionImpl.TaskInvokerImpl invokerimpl = new RemoteDaemonConnectionImpl.TaskInvokerImpl(
					connection, environmentidentifier);
			WeakReference<TaskInvoker> weakref = new WeakReference<>(invokerimpl);
			connection.addCloseListener(new RMIConnection.CloseListener() {
				//reference to keep the proxy alive until the connection is present
				@SuppressWarnings("unused")
				private TaskInvoker strongFactoryRef = invokerimpl;

				@Override
				public void onConnectionClosed() {
					System.out.println("Cluster client disconnected: " + id);
					clientClusterTaskInvokers.remove(weakref);
				}
			});
			clientClusterTaskInvokers.add(weakref);
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

	@RMIWrap(TaskInvokerRMIWrapper.class)
	private class LocalDaemonClusterInvoker implements TaskInvoker {
		private final Path clusterMirrorDirectory;
		private final ClassLoaderResolverRegistry connectionClassLoaderRegistry;

		public LocalDaemonClusterInvoker(ClassLoaderResolverRegistry connectionclregistry,
				SakerPath clusterMirrorDirectory) {
			this.connectionClassLoaderRegistry = connectionclregistry;
			this.clusterMirrorDirectory = clusterMirrorDirectory == null ? null
					: LocalFileProvider.toRealPath(clusterMirrorDirectory);
		}

		@Override
		public void run(ExecutionContext executioncontext, TaskInvokerInformation invokerinformation,
				TaskInvocationContext context) throws Exception {
			if (!(executioncontext instanceof InternalExecutionContext)) {
				//safety check this here, later than later
				//as we're downcasting the execution context
				throw new IllegalArgumentException(
						"Invalid execution context instance: " + ObjectUtils.classNameOf(executioncontext));
			}
			ExecutionPathConfiguration realpathconfig = executioncontext.getPathConfiguration();
			ProviderHolderPathKey workingpathkey = realpathconfig.getWorkingDirectoryPathKey();
			SakerProjectCache project = getProjectImpl(workingpathkey).project;
			Path modifiedmirrordir;
			if (clusterMirrorDirectory == null) {
				modifiedmirrordir = null;
			} else {
				//XXX maybe put a notice text file about the origin of the directory
				modifiedmirrordir = getMirrorDirectoryPathForWorkingDirectory(clusterMirrorDirectory, workingpathkey);
			}

			InternalBuildTrace internalbuildtrace = ((InternalExecutionContext) executioncontext)
					.internalGetBuildTrace();
			internalbuildtrace.startBuildCluster(environment, modifiedmirrordir);
			ClassLoaderResolverRegistry executionclregistry = new ClassLoaderResolverRegistry(
					environment.getClassLoaderResolverRegistry());
			String resolverid = createClusterTaskInvokerRMIRegistryClassResolverId(realpathconfig);
			connectionClassLoaderRegistry.register(resolverid, executionclregistry);
			try {
				try {
					project.clusterStarting(realpathconfig, executioncontext.getRepositoryConfiguration(),
							executioncontext.getScriptConfiguration(), executioncontext.getUserParameters(),
							modifiedmirrordir, invokerinformation.getDatabaseConfiguration(), executioncontext,
							executionclregistry);
				} catch (Exception e) {
					//XXX reify exception
					throw new IOException(e);
				}

				SakerExecutionCache execcache = project.getExecutionCache();

				FileMirrorHandler mirrorhandler = project.getClusterMirrorHandler();
				SakerEnvironment executionenvironment = project.getExecutionCache().getRecordingEnvironment();

				Object execkey = environment.getStartExecutionKey();
				try {
					ClusterTaskInvoker clusterinvoker = new ClusterTaskInvoker(environment, executionenvironment,
							executioncontext, mirrorhandler, execcache.getLoadedBuildRepositories(),
							project.getClusterContentDatabase(), execcache.getLoadedScriptProviderLocators());
					clusterinvoker.run(context);
				} finally {
					project.clusterFinished(execkey);
				}
			} finally {
				internalbuildtrace.endBuildCluster();
				connectionClassLoaderRegistry.unregister(resolverid, executionclregistry);
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
		public TaskInvoker getClusterTaskInvoker() {
			//TODO we probably need to adjust the incoming rmi connection class resolver registry. Should we disable multi-hop rmi cluster usage?
			return subject.getClusterTaskInvoker();
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
