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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

import saker.build.daemon.DaemonEnvironment;
import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.launching.LaunchConfigUtils;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.ThrowingFunction;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.NetworkUtils;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.osnative.NativeLibs;

public final class SakerIDEPlugin implements Closeable {
	private static final PluginEnvironmentState PLUGIN_ENVIRONMENT_STATE_NOT_INITIALIZED = new PluginEnvironmentState(
			new IllegalStateException("Saker.build plugin not initialized."));

	public interface PluginResourceListener {
		/**
		 * @param environment
		 *            The environment that is being closed.
		 */
		public default void environmentClosing(SakerEnvironmentImpl environment) {
		}

		/**
		 * @param environment
		 *            The environment that has been created.
		 */
		public default void environmentCreated(SakerEnvironmentImpl environment) {
		}
	}

	private static final String CONFIG_FILE_ROOT_OBJECT_NAME = "saker.build.ide.plugin.config";
	public static final String IDE_PLUGIN_PROPERTIES_FILE_NAME = "." + CONFIG_FILE_ROOT_OBJECT_NAME;

	private Path pluginDirectory;
	private Path sakerJarPath;

	private Path pluginConfigurationFilePath;

	private final ReadWriteLock configurationChangeReadWriteLock = new ReentrantReadWriteLock();
	private final Lock configurationChangeReadLock = configurationChangeReadWriteLock.readLock();
	private final Lock configurationChangeWriteLock = configurationChangeReadWriteLock.writeLock();

	private PluginEnvironmentState pluginDaemonEnvironment = PLUGIN_ENVIRONMENT_STATE_NOT_INITIALIZED;

	private final Object projectsLock = new Object();
	private final ConcurrentHashMap<Object, SakerIDEProject> projects = new ConcurrentHashMap<>();

	private IDEPluginProperties pluginProperties = SimpleIDEPluginProperties.empty();

	private final Set<ExceptionDisplayer> exceptionDisplayers = Collections
			.synchronizedSet(ObjectUtils.newSetFromMap(new WeakHashMap<>()));

	private final Set<PluginResourceListener> pluginResourceListeners = Collections
			.synchronizedSet(ObjectUtils.newSetFromMap(new WeakHashMap<>()));

	private ThreadWorkPool workPool;

	private boolean closed = false;

	public SakerIDEPlugin() {
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

	public void addPluginResourceListener(PluginResourceListener listener) {
		if (listener == null) {
			return;
		}
		pluginResourceListeners.add(listener);
	}

	public void removePluginResourceListener(PluginResourceListener listener) {
		if (listener == null) {
			return;
		}
		pluginResourceListeners.remove(listener);
	}

	/**
	 * Initializes this plugin instance.
	 * <p>
	 * This method does not fail. (Unless conditions specified in the thrown types are satisfied.)
	 * 
	 * @param sakerJarPath
	 *            The JAR path to the saker build system release.
	 * @param plugindirectory
	 *            The plugin directory where the plugin can store its data.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalStateException
	 *             If the instance is already initialized.
	 */
	public void initialize(Path sakerJarPath, Path plugindirectory) throws NullPointerException, IllegalStateException {
		Objects.requireNonNull(sakerJarPath, "saker JAR path");
		Objects.requireNonNull(plugindirectory, "plugin directory");
		configurationChangeWriteLock.lock();
		try {
			if (pluginDirectory != null) {
				throw new IllegalStateException("Plugin already initialized.");
			}
			try {
				NativeLibs.init(plugindirectory.resolve("nativelibs"));
			} catch (IOException e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to initialize native libraries.", e);
			}
			//the threads should be daemon
			workPool = ThreadUtils.newDynamicWorkPool(new ThreadGroup("Saker.build plugin worker"), "Worker-", null,
					true);

			this.pluginDirectory = plugindirectory;
			this.pluginConfigurationFilePath = this.pluginDirectory.resolve(IDE_PLUGIN_PROPERTIES_FILE_NAME);
			this.sakerJarPath = sakerJarPath;
			try (InputStream in = Files.newInputStream(pluginConfigurationFilePath)) {
				XMLStructuredReader reader = new XMLStructuredReader(in);
				try (StructuredObjectInput configurationobj = reader.readObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
					pluginProperties = IDEPersistenceUtils.readIDEPluginProperties(configurationobj);
				}
			} catch (NoSuchFileException e) {
			} catch (IOException e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to load plugin configuration.", e);
				pluginProperties = SimpleIDEPluginProperties.empty();
			}
		} finally {
			configurationChangeWriteLock.unlock();
		}
	}

	public void start(DaemonLaunchParameters daemonparams, SakerPath keystorepath,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator)
			throws IOException, IllegalStateException {
		startImpl(daemonparams, keystorepath, sslcontextcreator);
	}

	private void startImpl(DaemonLaunchParameters daemonparams, SakerPath keystorepath,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator)
			throws IOException, IllegalStateException {
		LocalDaemonEnvironment ndaemonenv = null;
		configurationChangeWriteLock.lock();
		Lock tounlock = configurationChangeWriteLock;
		try {
			if (closed) {
				return;
			}
			if (this.pluginDaemonEnvironment != PLUGIN_ENVIRONMENT_STATE_NOT_INITIALIZED) {
				throw new IllegalStateException("Already started.");
			}
			PluginEnvironmentState npluginstate;
			try {
				npluginstate = createPluginEnvironmentStateWithSSL(null, KeyStorePathHash.create(keystorepath),
						sslcontextcreator);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR,
						"Failed to intialize SSL for plugin daemon. Running in local mode.", e);
				//will have no ssl, and we turn off all server config for the daemon
				//not to allow unauthorized access
				daemonparams = SakerIDESupportUtils.cleanParametersForFailedSSL(daemonparams);
				npluginstate = new PluginEnvironmentState();
			}

			ndaemonenv = new LocalDaemonEnvironment(sakerJarPath, daemonparams);
			LocalDaemonEnvironment localdaemonenvtoclose = ndaemonenv;

			ndaemonenv.setServerSocketFactory(npluginstate.serverSocketFactory);
			ndaemonenv.setConnectionSocketFactory(npluginstate.socketFactory);
			ndaemonenv.setSslKeystorePath(npluginstate.getKeyStorePath());

			try {
				ndaemonenv.start();
				npluginstate.init(ndaemonenv);
				npluginstate = new PluginEnvironmentState(ndaemonenv);
				localdaemonenvtoclose = null;
			} catch (Throwable e) {
				try {
					ndaemonenv = null;
					npluginstate = new PluginEnvironmentState(e);
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				//the exception is thrown, no need to call the display exception hooks here
				throw e;
			} finally {
				IOUtils.close(localdaemonenvtoclose);
				this.pluginDaemonEnvironment = npluginstate;
			}
			if (ndaemonenv != null) {
				//downgrade to read lock when calling listeners
				configurationChangeReadLock.lock();
				tounlock = configurationChangeReadLock;
				configurationChangeWriteLock.unlock();

				SakerEnvironmentImpl openedenv = ndaemonenv.getSakerEnvironment();
				callListeners(ImmutableUtils.makeImmutableList(pluginResourceListeners),
						l -> l.environmentCreated(openedenv));
			}
		} finally {
			tounlock.unlock();
		}
	}

	public Map<String, RemoteDaemonConnection> connectToDaemonsFromPluginEnvironment(
			Collection<? extends DaemonConnectionIDEProperty> connectionproperties)
			throws InterruptedException, IOException {
		return connectToDaemonsFromDaemonEnvironment(connectionproperties, getPluginDaemonEnvironment());
	}

	public Map<String, RemoteDaemonConnection> connectToDaemonsFromDaemonEnvironment(
			Collection<? extends DaemonConnectionIDEProperty> connectionproperties, DaemonEnvironment daemonenv)
			throws InterruptedException, IOException {
		Map<String, RemoteDaemonConnection> result = new HashMap<>();
		for (DaemonConnectionIDEProperty prop : connectionproperties) {
			String address = prop.getNetAddress();
			if (ObjectUtils.isNullOrEmpty(address)) {
				throw new IllegalArgumentException("Empty address");
			}
			String connname = prop.getConnectionName();
			if (ObjectUtils.isNullOrEmpty(connname)) {
				throw new IllegalArgumentException("Empty name.");
			}
			if (result.containsKey(connname)) {
				throw new IllegalArgumentException("Duplicate connection names: " + connname);
			}
			result.put(connname, null);
		}
		Semaphore sem = new Semaphore(0);
		int size = result.size();
		IOException[] exc = { null };
		for (DaemonConnectionIDEProperty prop : connectionproperties) {
			workPool.offer(() -> {
				String netaddress = null;
				try {
					netaddress = prop.getNetAddress();
					InetSocketAddress socketaddress = NetworkUtils.parseInetSocketAddress(netaddress,
							DaemonLaunchParameters.DEFAULT_PORT);

					RemoteDaemonConnection connection = daemonenv.connectTo(socketaddress);
					result.put(prop.getConnectionName(), connection);
				} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
						| AssertionError | Exception e) {
					synchronized (exc) {
						exc[0] = IOUtils.addExc(exc[0], e);
					}
					this.displayException(SakerLog.SEVERITY_ERROR, "Failed to connect to daemon at: " + netaddress, e);
				} catch (Throwable e) {
					synchronized (exc) {
						exc[0] = IOUtils.addExc(exc[0], e);
					}
					try {
						this.displayException(SakerLog.SEVERITY_ERROR, "Failed to connect to daemon at: " + netaddress,
								e);
					} catch (Throwable e2) {
						e.addSuppressed(e2);
					}
					throw e;
				} finally {
					sem.release();
				}
			});
		}
		sem.acquire(size);
		IOUtils.throwExc(exc[0]);
		return result;
	}

	public final DaemonEnvironment getPluginDaemonEnvironment() throws IOException {
		configurationChangeReadLock.lock();
		try {
			return getPluginDaemonEnvironmentReadLocked();
		} finally {
			configurationChangeReadLock.unlock();
		}
	}

	private LocalDaemonEnvironment getPluginDaemonEnvironmentReadLocked() throws IOException {
		PluginEnvironmentState envstate = this.pluginDaemonEnvironment;
		LocalDaemonEnvironment env = envstate.environment;
		if (env != null) {
			return env;
		}
		throw new IOException("Plugin daemon environment not available.", envstate.initializationException);
	}

	public final SakerEnvironmentImpl getPluginEnvironment() throws IOException {
		configurationChangeReadLock.lock();
		try {
			return getPluginDaemonEnvironmentReadLocked().getSakerEnvironment();
		} finally {
			configurationChangeReadLock.unlock();
		}
	}

	public final SakerIDEProject getProject(Object key) {
		return projects.get(key);
	}

	public final SakerIDEProject getOrCreateProject(Object key) {
		synchronized (projectsLock) {
			SakerIDEProject project = projects.get(key);
			if (project != null) {
				return project;
			}
			SakerIDEProject created = new SakerIDEProject(this);
			projects.put(key, created);
			return created;
		}
	}

	public final void closeProject(Object key) throws IOException {
		SakerIDEProject project;
		synchronized (projectsLock) {
			project = projects.remove(key);
		}
		if (project != null) {
			project.close();
		}
	}

	@Override
	public void close() throws IOException {
		IOException exc = null;
		configurationChangeWriteLock.lock();
		try {
			closed = true;
			try {
				closeProjects();
			} catch (IOException e) {
				exc = IOUtils.addExc(exc, e);
			}
			exc = IOUtils.closeExc(exc, pluginDaemonEnvironment.environment);
		} finally {
			configurationChangeWriteLock.unlock();
		}
		try {
			workPool.closeInterruptible();
		} catch (IllegalThreadStateException | ParallelExecutionException e) {
			exc = IOUtils.addExc(exc, e);
		} catch (InterruptedException e) {
			//reinterrupt
			Thread.currentThread().interrupt();
			exc = IOUtils.addExc(exc, e);
		}
		IOUtils.throwExc(exc);
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

	public final IDEPluginProperties getIDEPluginProperties() {
		return pluginProperties;
	}

	public final boolean setIDEPluginProperties(IDEPluginProperties properties) throws IOException {
		properties = properties == null ? SimpleIDEPluginProperties.empty()
				: SimpleIDEPluginProperties.builder(properties).build();
		configurationChangeWriteLock.lock();
		try {
			if (this.pluginProperties.equals(properties)) {
				return false;
			}
			writePluginPropertiesFile(properties);
			this.pluginProperties = properties;
			return true;
		} finally {
			configurationChangeWriteLock.unlock();
		}
	}

	public void updateForPluginProperties(IDEPluginProperties properties,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator) {
		updateForPluginPropertiesImpl(properties, sslcontextcreator);
	}

	private void updateForPluginPropertiesImpl(IDEPluginProperties properties,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator) {
		properties = properties == null ? SimpleIDEPluginProperties.empty()
				: SimpleIDEPluginProperties.builder(properties).build();
		DaemonLaunchParameters newlaunchparams = createDaemonLaunchParameters(properties);

		configurationChangeWriteLock.lock();
		Lock tounlock = configurationChangeWriteLock;
		try {
			PluginEnvironmentState envstate = this.pluginDaemonEnvironment;
			LocalDaemonEnvironment plugindaemonenv = envstate.environment;
			KeyStorePathHash keystore;
			String keystorepathprop = properties.getKeyStorePath();
			if (!ObjectUtils.isNullOrEmpty(keystorepathprop)) {
				try {
					SakerPath keystorepath = SakerPath.valueOf(keystorepathprop);
					keystore = KeyStorePathHash.create(keystorepath);
				} catch (Exception e) {
					displayException(SakerLog.SEVERITY_ERROR, "Failed to open keystore: " + keystorepathprop, e);
					keystore = KeyStorePathHash.EMPTY;
					newlaunchparams = SakerIDESupportUtils.cleanParametersForFailedSSL(newlaunchparams);
				}
			} else {
				keystore = KeyStorePathHash.EMPTY;
			}
			if (plugindaemonenv != null && newlaunchparams.equals(plugindaemonenv.getLaunchParameters())
					&& envstate.isKeyStoreHashEquals(keystore)) {
				return;
			}
			//else we go ahead and reload the daemon
			tounlock = null;
			//method will unlock
			reloadPluginDaemonWriteLocked(newlaunchparams, keystore, sslcontextcreator);
		} finally {
			if (tounlock != null) {
				tounlock.unlock();
			}
		}
	}

	public void forceReloadPluginDaemon(DaemonLaunchParameters newlaunchparams, SakerPath keystorepath,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator) {
		configurationChangeWriteLock.lock();

		KeyStorePathHash keystore;
		try {
			keystore = KeyStorePathHash.create(keystorepath);
		} catch (IOException e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to open keystore: " + keystorepath, e);
			keystore = KeyStorePathHash.EMPTY;
			newlaunchparams = SakerIDESupportUtils.cleanParametersForFailedSSL(newlaunchparams);
		}

		reloadPluginDaemonWriteLocked(newlaunchparams, keystore, sslcontextcreator);
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

	//This method is responsible for unlocking the write lock
	private void reloadPluginDaemonWriteLocked(DaemonLaunchParameters newlaunchparams, KeyStorePathHash keystore,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator) {
		Lock tounlock = configurationChangeWriteLock;
		try {
			List<PluginResourceListener> pluginlisteners = ImmutableUtils.makeImmutableList(pluginResourceListeners);

			PluginEnvironmentState currentenvironmentstate = this.pluginDaemonEnvironment;
			LocalDaemonEnvironment currentdaemonenv = currentenvironmentstate.environment;
			if (currentdaemonenv != null) {
				SakerEnvironmentImpl closingenv = currentdaemonenv.getSakerEnvironment();
				try {
					callListeners(pluginlisteners, l -> l.environmentClosing(closingenv));
					currentdaemonenv.close();
				} finally {
					PluginEnvironmentState npluginstate = new PluginEnvironmentState(
							new IllegalStateException("Plugin build environment closed."));
					npluginstate.copyKeyStoreAndSocketFactories(currentenvironmentstate);
					this.pluginDaemonEnvironment = npluginstate;
				}
			}

			PluginEnvironmentState npluginstate;
			try {
				npluginstate = createPluginEnvironmentStateWithSSL(currentenvironmentstate, keystore,
						sslcontextcreator);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR,
						"Failed to intialize SSL for plugin daemon. Running in local mode.", e);
				//will have no ssl, and we turn off all server config for the daemon
				//not to allow unauthorized access
				newlaunchparams = SakerIDESupportUtils.cleanParametersForFailedSSL(newlaunchparams);
				npluginstate = new PluginEnvironmentState();
			}

			LocalDaemonEnvironment ndaemonenv = new LocalDaemonEnvironment(sakerJarPath, newlaunchparams);
			ndaemonenv.setServerSocketFactory(npluginstate.serverSocketFactory);
			ndaemonenv.setConnectionSocketFactory(npluginstate.socketFactory);
			ndaemonenv.setSslKeystorePath(npluginstate.getKeyStorePath());

			LocalDaemonEnvironment localdaemonenvtoclose = ndaemonenv;
			try {
				ndaemonenv.start();
				npluginstate.init(ndaemonenv);
				localdaemonenvtoclose = null;
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				npluginstate = new PluginEnvironmentState(e);
				ndaemonenv = null;
				displayException(SakerLog.SEVERITY_ERROR, "Failed to start plugin daemon environment.", e);
				return;
			} catch (Throwable e) {
				ndaemonenv = null;
				npluginstate = new PluginEnvironmentState(e);
				//no need to display the exception here, as the exception is thrown
				throw e;
			} finally {
				if (npluginstate != null) {
					this.pluginDaemonEnvironment = npluginstate;
				}

				try {
					IOUtils.close(localdaemonenvtoclose);
				} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
						| AssertionError | Exception e) {
					displayException(SakerLog.SEVERITY_WARNING, "Failed to close daemon environment.", e);
				}
			}
			if (ndaemonenv != null) {
				//downgrade to read lock when calling listeners
				configurationChangeReadLock.lock();
				tounlock = configurationChangeReadLock;
				configurationChangeWriteLock.unlock();

				SakerEnvironmentImpl openedenv = ndaemonenv.getSakerEnvironment();
				callListeners(pluginlisteners, l -> l.environmentCreated(openedenv));
			}
		} finally {
			tounlock.unlock();
		}
	}

	private static PluginEnvironmentState createPluginEnvironmentStateWithSSL(
			PluginEnvironmentState currentenvironmentstate, KeyStorePathHash keystore,
			ThrowingFunction<? super SakerPath, ? extends SSLContext> sslcontextcreator) throws Exception {
		PluginEnvironmentState npluginstate = new PluginEnvironmentState();
		npluginstate.setKeyStorePathHash(keystore);
		if (currentenvironmentstate != null && npluginstate.isKeyStoreHashEquals(currentenvironmentstate)) {
			//the keystore didn't change, no need to construct new SSLContext, can use socket factories from the old one
			npluginstate.copySocketFactories(currentenvironmentstate);
		} else {
			//the keystore changed, create a new ssl context
			if (keystore != null && keystore.keyStorePath != null) {
				Objects.requireNonNull(sslcontextcreator, "SSL context creator");
				SSLContext sslcontext = sslcontextcreator.apply(keystore.keyStorePath);
				Objects.requireNonNull(sslcontext, "SSL context");
				npluginstate.socketFactory = LaunchConfigUtils.getSocketFactory(sslcontext);
				npluginstate.serverSocketFactory = LaunchConfigUtils.getServerSocketFactory(sslcontext);
			}
		}
		return npluginstate;
	}

	public static Map<String, String> entrySetToMap(Set<? extends Entry<String, String>> entries) {
		if (entries == null) {
			return null;
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (Entry<String, String> entry : entries) {
			String key = entry.getKey();
			if (key == null) {
				throw new IllegalArgumentException("Entry key is null: " + entry);
			}
			if (result.containsKey(key)) {
				throw new IllegalArgumentException("Duplicate entry: " + entry);
			}
			result.put(key, entry.getValue());
		}
		return ImmutableUtils.unmodifiableMap(result);
	}

	public static Set<? extends Entry<String, String>> makeImmutableEntrySet(
			Set<? extends Entry<String, String>> entries) {
		if (entries == null) {
			return null;
		}
		LinkedHashSet<Entry<String, String>> result = new LinkedHashSet<>();
		for (Entry<String, String> entry : entries) {
			result.add(ImmutableUtils.makeImmutableMapEntry(entry));
		}
		return ImmutableUtils.unmodifiableSet(result);
	}

	public DaemonLaunchParameters createDaemonLaunchParameters(IDEPluginProperties pluginproperties) {
		String storagedirprop = pluginproperties.getStorageDirectory();
		SakerPath storagedirconfiguration = null;
		if (storagedirprop != null) {
			try {
				storagedirconfiguration = SakerPath.valueOf(storagedirprop);
			} catch (InvalidPathFormatException e) {
				//failed to parse path, consider it to be not set
			}
		}
		SakerPath daemonstoragedirectory;
		if (storagedirconfiguration == null) {
			daemonstoragedirectory = SakerPath.valueOf(SakerEnvironmentImpl.getDefaultStorageDirectory());
		} else {
			if (storagedirconfiguration.isRelative()) {
				daemonstoragedirectory = SakerPath.valueOf(pluginDirectory).resolve(storagedirconfiguration);
			} else {
				daemonstoragedirectory = storagedirconfiguration;
			}
		}

		DaemonLaunchParameters.Builder builder = DaemonLaunchParameters.builder();
		builder.setStorageDirectory(daemonstoragedirectory);
		builder.setUserParameters(entrySetToMap(pluginproperties.getUserParameters()));
		if (SakerIDESupportUtils.getBooleanValueOrDefault(pluginproperties.getActsAsServer(), false)) {
			builder.setActsAsServer(true);
		}
		builder.setPort(SakerIDESupportUtils.getPortValueOrNull(pluginproperties.getPort()));
		DaemonLaunchParameters daemonparams = builder.build();
		return daemonparams;
	}

	private void writePluginPropertiesFile(IDEPluginProperties pluginConfiguration) throws IOException {
		Path propfilepath = pluginConfigurationFilePath;
		Path tempfilepath = propfilepath.resolveSibling(propfilepath.getFileName() + "." + UUID.randomUUID() + ".temp");
		try (OutputStream os = Files.newOutputStream(tempfilepath);
				XMLStructuredWriter writer = new XMLStructuredWriter(os);
				StructuredObjectOutput objwriter = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
			IDEPersistenceUtils.writeIDEPluginProperties(objwriter, pluginConfiguration);
		}
		Files.move(tempfilepath, propfilepath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void closeProjects() throws IOException {
		List<SakerIDEProject> copiedprojects;
		synchronized (projectsLock) {
			copiedprojects = ImmutableUtils.makeImmutableList(projects.values());
			projects.clear();
		}
		IOException exc = null;
		for (SakerIDEProject p : copiedprojects) {
			try {
				p.close();
			} catch (IOException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
	}

	private static final class PluginEnvironmentState {
		protected LocalDaemonEnvironment environment;
		protected Throwable initializationException;

		protected KeyStorePathHash keyStorePathHash = KeyStorePathHash.EMPTY;

		protected SocketFactory socketFactory;
		protected ServerSocketFactory serverSocketFactory;

		public PluginEnvironmentState() {
		}

		public PluginEnvironmentState(LocalDaemonEnvironment environment) {
			this.environment = environment;
		}

		public PluginEnvironmentState(Throwable initializationException) {
			this.initializationException = initializationException;
		}

		public void init(LocalDaemonEnvironment environment) {
			this.environment = environment;
		}

		public void initFailed(Throwable initializationException) {
			this.initializationException = initializationException;
		}

		public void copyKeyStoreAndSocketFactories(PluginEnvironmentState state) {
			this.socketFactory = state.socketFactory;
			this.serverSocketFactory = state.serverSocketFactory;
			this.keyStorePathHash = state.keyStorePathHash;
		}

		public void copySocketFactories(PluginEnvironmentState state) {
			this.socketFactory = state.socketFactory;
			this.serverSocketFactory = state.serverSocketFactory;
		}

		public void setKeyStorePathHash(KeyStorePathHash keyStorePathHash) {
			this.keyStorePathHash = keyStorePathHash;
		}

		public boolean isKeyStoreHashEquals(PluginEnvironmentState state) {
			return Objects.equals(this.keyStorePathHash, state.keyStorePathHash);
		}

		public boolean isKeyStoreHashEquals(KeyStorePathHash keystorepathhash) {
			return Objects.equals(this.keyStorePathHash, keystorepathhash);
		}

		public SakerPath getKeyStorePath() {
			return keyStorePathHash == null ? null : keyStorePathHash.keyStorePath;
		}
	}

	private static final class KeyStorePathHash {
		protected static final KeyStorePathHash EMPTY = new KeyStorePathHash(null, null);

		protected final SakerPath keyStorePath;
		protected final String keyStoreHash;

		public KeyStorePathHash(SakerPath keyStorePath, String keyStoreHash) {
			this.keyStorePath = keyStorePath;
			this.keyStoreHash = keyStoreHash;
		}

		public static KeyStorePathHash create(SakerPath keyStorePath) throws IOException {
			if (keyStorePath == null) {
				return EMPTY;
			} else {
				try {
					String hash = StringUtils
							.toHexString(LocalFileProvider.getInstance().hash(keyStorePath, "SHA1").getHash());
					return new KeyStorePathHash(keyStorePath, hash);
				} catch (NoSuchAlgorithmException e) {
					throw new RuntimeException("SHA1 hash algorithm not found.", e);
				}
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[keyStorePath=");
			builder.append(keyStorePath);
			builder.append(", keyStoreHash=");
			builder.append(keyStoreHash);
			builder.append("]");
			return builder.toString();
		}

	}
}
