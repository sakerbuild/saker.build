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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.net.ServerSocketFactory;

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
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.NetworkUtils;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.osnative.NativeLibs;

public final class SakerIDEPlugin implements Closeable {
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

	private final Object configurationChangeLock = new Object();

	private LocalDaemonEnvironment pluginDaemonEnvironment;

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

	public void initialize(Path sakerJarPath, Path plugindirectory) throws IOException {
		Objects.requireNonNull(sakerJarPath, "saker JAR path");
		Objects.requireNonNull(plugindirectory, "plugin directory");
		synchronized (configurationChangeLock) {
			if (pluginDirectory != null) {
				throw new IllegalStateException("Plugin already initialized.");
			}
			NativeLibs.init(plugindirectory.resolve("nativelibs"));
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
				displayException(e);
				pluginProperties = SimpleIDEPluginProperties.empty();
			}
		}
	}

	public void start(DaemonLaunchParameters daemonparams) throws IOException {
		synchronized (configurationChangeLock) {
			if (closed) {
				return;
			}
			if (this.pluginDaemonEnvironment != null) {
				throw new IllegalStateException("Already started.");
			}
			LocalDaemonEnvironment ndaemonenv = new LocalDaemonEnvironment(sakerJarPath, daemonparams, null);
			LocalDaemonEnvironment localdaemonenvtoclose = ndaemonenv;
			try {
				ndaemonenv.setServerSocketFactory(ServerSocketFactory.getDefault());
				ndaemonenv.start();
				this.pluginDaemonEnvironment = ndaemonenv;
				localdaemonenvtoclose = null;
			} catch (IOException e) {
				displayException(e);
				throw e;
			} finally {
				IOUtils.close(localdaemonenvtoclose);
			}
			SakerEnvironmentImpl openedenv = this.pluginDaemonEnvironment.getSakerEnvironment();
			callListeners(ImmutableUtils.makeImmutableList(pluginResourceListeners),
					l -> l.environmentCreated(openedenv));
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
		int[] rescounter = { result.size() };
		IOException[] exc = { null };
		synchronized (rescounter) {
			for (DaemonConnectionIDEProperty prop : connectionproperties) {
				workPool.offer(() -> {
					try {
						InetSocketAddress socketaddress = NetworkUtils.parseInetSocketAddress(prop.getNetAddress(),
								DaemonLaunchParameters.DEFAULT_PORT);

						RemoteDaemonConnection connection = daemonenv.connectTo(socketaddress);
						result.put(prop.getConnectionName(), connection);
					} catch (IOException e) {
						synchronized (exc) {
							exc[0] = IOUtils.addExc(exc[0], e);
						}
						this.displayException(e);
					} catch (Throwable e) {
						synchronized (exc) {
							exc[0] = IOUtils.addExc(exc[0], e);
						}
						this.displayException(e);
					} finally {
						synchronized (rescounter) {
							rescounter[0]--;
							rescounter.notify();
						}
					}
				});
			}
			while (rescounter[0] > 0) {
				rescounter.wait();
			}
		}
		IOUtils.throwExc(exc[0]);
		return result;
	}

	public final DaemonEnvironment getPluginDaemonEnvironment() {
		synchronized (configurationChangeLock) {
			return this.pluginDaemonEnvironment;
		}
	}

	public final SakerEnvironmentImpl getPluginEnvironment() {
		synchronized (configurationChangeLock) {
			return this.pluginDaemonEnvironment.getSakerEnvironment();
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
		synchronized (configurationChangeLock) {
			closed = true;
			try {
				closeProjects();
			} catch (IOException e) {
				exc = IOUtils.addExc(exc, e);
			}
			exc = IOUtils.closeExc(exc, pluginDaemonEnvironment);
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

	public void displayException(Throwable e) {
		int callcount = callListeners(ImmutableUtils.makeImmutableList(exceptionDisplayers), d->d.displayException(e));
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
		synchronized (configurationChangeLock) {
			if (this.pluginProperties.equals(properties)) {
				return;
			}
			this.pluginProperties = properties;
			writePluginPropertiesFile(properties);
		}
	}

	public void updateForPluginProperties(IDEPluginProperties properties) {
		properties = properties == null ? SimpleIDEPluginProperties.empty()
				: SimpleIDEPluginProperties.builder(properties).build();
		synchronized (configurationChangeLock) {
			DaemonLaunchParameters newlaunchparams = createDaemonLaunchParameters(properties);
			LocalDaemonEnvironment plugindaemonenv = this.pluginDaemonEnvironment;
			if (plugindaemonenv == null || !newlaunchparams.equals(plugindaemonenv.getLaunchParameters())) {
				reloadPluginDaemonLocked(newlaunchparams);
			}
		}
	}

	public void forceReloadPluginDaemon(DaemonLaunchParameters newlaunchparams) {
		synchronized (configurationChangeLock) {
			reloadPluginDaemonLocked(newlaunchparams);
		}
	}

	private <L> int callListeners(Iterable<L> listeners, Consumer<? super L> caller) {
		int c = 0;
		for (L l : listeners) {
			++c;
			try {
				caller.accept(l);
			} catch (Exception e) {
				displayException(e);
			}
		}
		return c;
	}

	private void reloadPluginDaemonLocked(DaemonLaunchParameters newlaunchparams) {
		List<PluginResourceListener> pluginlisteners = ImmutableUtils.makeImmutableList(pluginResourceListeners);

		LocalDaemonEnvironment currentdaemonenv = this.pluginDaemonEnvironment;
		if (currentdaemonenv != null) {
			SakerEnvironmentImpl closingenv = currentdaemonenv.getSakerEnvironment();
			try {
				callListeners(pluginlisteners, l -> l.environmentClosing(closingenv));
				currentdaemonenv.close();
			} finally {
				this.pluginDaemonEnvironment = null;
			}
		}

		LocalDaemonEnvironment ndaemonenv = new LocalDaemonEnvironment(sakerJarPath, newlaunchparams, null);
		LocalDaemonEnvironment localdaemonenvtoclose = ndaemonenv;
		try {
			ndaemonenv.start();
			this.pluginDaemonEnvironment = ndaemonenv;
			localdaemonenvtoclose = null;
		} catch (IOException e) {
			displayException(e);
		} finally {
			try {
				IOUtils.close(localdaemonenvtoclose);
			} catch (IOException e) {
				displayException(e);
			}
		}
		SakerEnvironmentImpl openedenv = ndaemonenv.getSakerEnvironment();
		callListeners(pluginlisteners, l -> l.environmentCreated(openedenv));
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

		DaemonLaunchParameters daemonparams = DaemonLaunchParameters.builder()
				.setStorageDirectory(daemonstoragedirectory)
				.setUserParameters(entrySetToMap(pluginproperties.getUserParameters())).build();
		return daemonparams;
	}

	private void writePluginPropertiesFile(IDEPluginProperties pluginConfiguration) {
		Path propfilepath = pluginConfigurationFilePath;
		Path tempfilepath = propfilepath.resolveSibling(IDE_PLUGIN_PROPERTIES_FILE_NAME + ".temp");
		try (OutputStream os = Files.newOutputStream(tempfilepath);
				XMLStructuredWriter writer = new XMLStructuredWriter(os);
				StructuredObjectOutput objwriter = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
			IDEPersistenceUtils.writeIDEPluginProperties(objwriter, pluginConfiguration);
		} catch (Exception e) {
			displayException(e);
			return;
		}
		try {
			Files.move(tempfilepath, propfilepath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			displayException(e);
		}
	}

	private void closeProjects() throws IOException {
		List<SakerIDEProject> copiedprojects;
		synchronized (projectsLock) {
			copiedprojects = new ArrayList<>(projects.values());
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

	static final class ExceptionDisplayForEachConsumer implements Consumer<ExceptionDisplayer> {
		private final Throwable e;
		private boolean called;

		public ExceptionDisplayForEachConsumer(Throwable e) {
			this.e = e;
		}

		@Override
		public void accept(ExceptionDisplayer d) {
			d.displayException(e);
			called = true;
		}

		public boolean isCalled() {
			return called;
		}
	}
}
