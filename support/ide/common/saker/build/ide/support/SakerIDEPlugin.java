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
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
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

	public void start(DaemonLaunchParameters daemonparams, SSLContext sslcontext, SakerPath keystorepath)
			throws IOException, IllegalStateException {
		ServerSocketFactory serversocketfactory = null;
		SocketFactory socketfactory = null;
		if (sslcontext != null) {
			serversocketfactory = sslcontext.getServerSocketFactory();
			socketfactory = sslcontext.getSocketFactory();
		}
		startImpl(daemonparams, keystorepath, serversocketfactory, socketfactory);
	}

	private void startImpl(DaemonLaunchParameters daemonparams, SakerPath keystorepath,
			ServerSocketFactory serversocketfactory, SocketFactory socketfactory)
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

			ndaemonenv = new LocalDaemonEnvironment(sakerJarPath, daemonparams, null);
			LocalDaemonEnvironment localdaemonenvtoclose = ndaemonenv;
			ndaemonenv.setServerSocketFactory(serversocketfactory);
			ndaemonenv.setConnectionSocketFactory(socketfactory);
			ndaemonenv.setSslKeystorePath(keystorepath);

			PluginEnvironmentState npluginstate = null;
			try {
				ndaemonenv.start();
				npluginstate = new PluginEnvironmentState(ndaemonenv);
				localdaemonenvtoclose = null;
			} catch (Throwable e) {
				try {
					ndaemonenv = null;
					npluginstate = new PluginEnvironmentState(e);
					displayException(SakerLog.SEVERITY_ERROR, "Failed to start daemon environment.", e);
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				throw e;
			} finally {
				IOUtils.close(localdaemonenvtoclose);
				if (npluginstate != null) {
					//this should not ever be null, as it is always assigned even in case of exception,
					//but check anyway
					npluginstate.serverSocketFactory = serversocketfactory;
					npluginstate.socketFactory = socketfactory;
					npluginstate.keyStorePath = keystorepath;
				}
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

	@Deprecated
	public void start(DaemonLaunchParameters daemonparams) throws IOException {
		start(daemonparams, null, null);
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
				} catch (IOException e) {
					synchronized (exc) {
						exc[0] = IOUtils.addExc(exc[0], e);
					}
					this.displayException(SakerLog.SEVERITY_ERROR, "Failed to connect to daemon at: " + netaddress, e);
				} catch (Throwable e) {
					synchronized (exc) {
						exc[0] = IOUtils.addExc(exc[0], e);
					}
					this.displayException(SakerLog.SEVERITY_ERROR, "Failed to connect to daemon at: " + netaddress, e);
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

	@Deprecated
	public void displayException(Throwable e) {
		int callcount = callListeners(ImmutableUtils.makeImmutableList(exceptionDisplayers),
				d -> d.displayException(e));
		if (callcount == 0) {
			e.printStackTrace();
		}
	}

	public void displayException(int severity, String message, Throwable e) {
		int callcount = callListeners(ImmutableUtils.makeImmutableList(exceptionDisplayers),
				d -> d.displayException(severity, message, e));
		if (callcount == 0) {
			e.printStackTrace();
		}
	}

	public final IDEPluginProperties getIDEPluginProperties() {
		return pluginProperties;
	}

	public final void setIDEPluginProperties(IDEPluginProperties properties) {
		properties = properties == null ? SimpleIDEPluginProperties.empty()
				: SimpleIDEPluginProperties.builder(properties).build();
		configurationChangeWriteLock.lock();
		try {
			if (this.pluginProperties.equals(properties)) {
				return;
			}
			this.pluginProperties = properties;
			writePluginPropertiesFile(properties);
		} finally {
			configurationChangeWriteLock.unlock();
		}
	}

	public void updateForPluginProperties(IDEPluginProperties properties) {
		updateForPluginPropertiesImpl(properties, null, null, false);
	}

	public void updateForPluginProperties(IDEPluginProperties properties, SSLContext sslcontext,
			SakerPath keystorepath) {
		updateForPluginPropertiesImpl(properties, sslcontext, keystorepath, true);
	}

	private void updateForPluginPropertiesImpl(IDEPluginProperties properties, SSLContext sslcontext,
			SakerPath keystorepath, boolean updatessl) {
		properties = properties == null ? SimpleIDEPluginProperties.empty()
				: SimpleIDEPluginProperties.builder(properties).build();
		configurationChangeWriteLock.lock();
		Lock tounlock = configurationChangeWriteLock;
		try {
			DaemonLaunchParameters newlaunchparams = createDaemonLaunchParameters(properties);
			LocalDaemonEnvironment plugindaemonenv = this.pluginDaemonEnvironment.environment;
			if (plugindaemonenv == null || !newlaunchparams.equals(plugindaemonenv.getLaunchParameters())) {
				tounlock = null;
				//method will unlock
				reloadPluginDaemonWriteLocked(newlaunchparams, sslcontext, keystorepath, updatessl);
			}
		} finally {
			if (tounlock != null) {
				tounlock.unlock();
			}
		}
	}

	public void forceReloadPluginDaemon(DaemonLaunchParameters newlaunchparams) {
		configurationChangeWriteLock.lock();
		reloadPluginDaemonWriteLocked(newlaunchparams, null, null, false);

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
	private void reloadPluginDaemonWriteLocked(DaemonLaunchParameters newlaunchparams, SSLContext sslcontext,
			SakerPath keystorepath, boolean updatessl) {
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
					if (updatessl) {
						npluginstate.serverSocketFactory = (sslcontext == null ? null
								: sslcontext.getServerSocketFactory());
						npluginstate.socketFactory = (sslcontext == null ? null : sslcontext.getSocketFactory());
						npluginstate.keyStorePath = (keystorepath);
					} else {
						npluginstate.copySSLFields(currentenvironmentstate);
					}
					this.pluginDaemonEnvironment = npluginstate;
				}
			}

			LocalDaemonEnvironment ndaemonenv = new LocalDaemonEnvironment(sakerJarPath, newlaunchparams, null);
			if (updatessl) {
				ndaemonenv.setServerSocketFactory(sslcontext == null ? null : sslcontext.getServerSocketFactory());
				ndaemonenv.setConnectionSocketFactory(sslcontext == null ? null : sslcontext.getSocketFactory());
				ndaemonenv.setSslKeystorePath(keystorepath);
			} else {
				ndaemonenv.setServerSocketFactory(currentenvironmentstate.serverSocketFactory);
				ndaemonenv.setConnectionSocketFactory(currentenvironmentstate.socketFactory);
				ndaemonenv.setSslKeystorePath(currentenvironmentstate.keyStorePath);
			}

			LocalDaemonEnvironment localdaemonenvtoclose = ndaemonenv;
			PluginEnvironmentState npluginstate = null;
			try {
				ndaemonenv.start();
				npluginstate = new PluginEnvironmentState(ndaemonenv);
				localdaemonenvtoclose = null;
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				npluginstate = new PluginEnvironmentState(e);
				ndaemonenv = null;
				displayException(SakerLog.SEVERITY_ERROR, "Failed to start plugin daemon environment.", e);
				return;
			} catch (Throwable e) {
				try {
					ndaemonenv = null;
					npluginstate = new PluginEnvironmentState(e);
					displayException(SakerLog.SEVERITY_ERROR, "Failed to start plugin daemon environment.", e);
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				throw e;
			} finally {
				if (npluginstate != null) {
					if (updatessl) {
						npluginstate.serverSocketFactory = (sslcontext == null ? null
								: sslcontext.getServerSocketFactory());
						npluginstate.socketFactory = (sslcontext == null ? null : sslcontext.getSocketFactory());
						npluginstate.keyStorePath = (keystorepath);
					} else {
						npluginstate.copySSLFields(currentenvironmentstate);
					}
					this.pluginDaemonEnvironment = npluginstate;
				}

				try {
					IOUtils.close(localdaemonenvtoclose);
				} catch (IOException e) {
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

	private void writePluginPropertiesFile(IDEPluginProperties pluginConfiguration) {
		Path propfilepath = pluginConfigurationFilePath;
		Path tempfilepath = propfilepath.resolveSibling(IDE_PLUGIN_PROPERTIES_FILE_NAME + ".temp");
		try {
			try (OutputStream os = Files.newOutputStream(tempfilepath);
					XMLStructuredWriter writer = new XMLStructuredWriter(os);
					StructuredObjectOutput objwriter = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
				IDEPersistenceUtils.writeIDEPluginProperties(objwriter, pluginConfiguration);
			}
			Files.move(tempfilepath, propfilepath, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to write plugin configuration.", e);
		}
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
		protected final LocalDaemonEnvironment environment;
		protected final Throwable initializationException;
		protected SocketFactory socketFactory;
		protected ServerSocketFactory serverSocketFactory;
		protected SakerPath keyStorePath;

		public PluginEnvironmentState(LocalDaemonEnvironment environment) {
			this.environment = environment;
			this.initializationException = null;
		}

		public PluginEnvironmentState(Throwable initializationException) {
			this.environment = null;
			this.initializationException = initializationException;
		}

		public void copySSLFields(PluginEnvironmentState state) {
			this.socketFactory = state.socketFactory;
			this.serverSocketFactory = state.serverSocketFactory;
			this.keyStorePath = state.keyStorePath;
		}
	}
}
