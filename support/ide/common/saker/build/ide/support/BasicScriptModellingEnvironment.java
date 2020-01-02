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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileEventListener;
import saker.build.file.provider.FileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ScriptAccessorClassPathCacheKey;
import saker.build.runtime.execution.ScriptAccessorClassPathData;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.SimpleScriptParsingOptions;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.IOUtils;

public class BasicScriptModellingEnvironment implements ScriptModellingEnvironment {
	//TODO use environment load listener on the plugin
	private final SakerIDEPlugin plugin;
	private final SakerEnvironmentImpl environment;

	private final Map<Object, SpawnedEngine> accessorKeyModellingEngines = new HashMap<>();
	private final NavigableMap<SakerPath, SpawnedModel> models = new TreeMap<>();

	private ScriptModellingEnvironmentConfiguration configuration;
	private Object configurationVersionKey = new Object();

	private Map<FileProviderKey, ConcurrentNavigableMap<SakerPath, DirectoryWatcher>> watchers = new HashMap<>();
	private Set<WildcardPath> excludeWildcardPaths = Collections.emptyNavigableSet();

	private boolean closed = false;

	public BasicScriptModellingEnvironment(SakerIDEPlugin plugin, ScriptModellingEnvironmentConfiguration configuration,
			SakerEnvironmentImpl environment) {
		this.plugin = plugin;
		this.environment = environment;
		this.configuration = configuration;

		excludeWildcardPaths = ImmutableUtils.makeImmutableNavigableSet(configuration.getExcludedScriptPaths());

		ExecutionPathConfiguration pathconfig = this.configuration.getPathConfiguration();
		SakerPath workingdir = pathconfig.getWorkingDirectory();
		SakerFileProvider workingdirfp = pathconfig.getFileProvider(workingdir);
		installWatchers(workingdirfp, workingdir);
	}

	@Override
	public ScriptModellingEnvironmentConfiguration getConfiguration() {
		return configuration;
	}

	private boolean isPathExcluded(SakerPath path) {
		if (ObjectUtils.isNullOrEmpty(excludeWildcardPaths)) {
			return false;
		}
		for (WildcardPath wc : excludeWildcardPaths) {
			if (wc.includes(path)) {
				return true;
			}
		}
		return false;
	}

	public void setConfiguration(ScriptModellingEnvironmentConfiguration configuration) {
		synchronized (this) {
			//XXX delta check the configuration changes instead of uninstalling all
//			Set<PathKey> prevexcludedsearchpaths = this.configuration.getExcludedSearchPaths();
//			ExecutionScriptConfiguration prevscriptconfig = this.configuration.getScriptConfiguration();
//			ExecutionPathConfiguration prevpathconfig = this.configuration.getPathConfiguration();

			this.configuration = configuration;
			configurationVersionKey = new Object();

			uninstallWatchers();
			IOUtils.closePrint(accessorKeyModellingEngines.values());
			accessorKeyModellingEngines.clear();
			models.clear();

			excludeWildcardPaths = ImmutableUtils.makeImmutableNavigableSet(configuration.getExcludedScriptPaths());

			ExecutionPathConfiguration pathconfig = this.configuration.getPathConfiguration();
			SakerPath workingdir = pathconfig.getWorkingDirectory();
			SakerFileProvider workingdirfp = pathconfig.getFileProvider(workingdir);
			installWatchers(workingdirfp, workingdir);
		}
	}

	private void installWatchers(SakerFileProvider fp, SakerPath path) {
		FileProviderKey providerkey = fp.getProviderKey();
		installDirectoryWatcher(fp, path,
				watchers.computeIfAbsent(providerkey, Functionals.concurrentSkipListMapComputer()));
	}

	private void installDirectoryWatcher(SakerFileProvider fp, SakerPath path,
			ConcurrentNavigableMap<SakerPath, DirectoryWatcher> watchers) {
		if (isPathExcluded(path)) {
			//do not install watcher for this directory or children
			return;
		}
		DirectoryWatcher watcher = new DirectoryWatcher(this, fp, path, watchers, this.configurationVersionKey);
		installDirectoryWatcherLocked(fp, path, watchers, watcher);
	}

	private void installDirectoryWatcherLocked(SakerFileProvider fp, SakerPath path,
			ConcurrentNavigableMap<SakerPath, DirectoryWatcher> watchers, DirectoryWatcher watcher) {
		NavigableMap<String, ? extends FileEntry> direntries;
		synchronized (watcher) {
			try {
				FileEventListener.ListenerToken token = fp.addFileEventListener(path, watcher);
				watcher.initToken(token);
				watchers.put(path, watcher);
				direntries = fp.getDirectoryEntries(path);
				for (Entry<String, ? extends FileEntry> entry : direntries.entrySet()) {
					FileEntry attrs = entry.getValue();
					if (attrs.isRegularFile()) {
						SakerPath entrypath = path.resolve(entry.getKey());
						//construct the model my getting it
						getModelLocked(entrypath);
					}
				}
			} catch (IOException | RMIRuntimeException e) {
				e.printStackTrace();
				return;
			}
		}
		for (Entry<String, ? extends FileEntry> entry : direntries.entrySet()) {
			FileEntry attrs = entry.getValue();
			if (attrs.isDirectory()) {
				SakerPath entrypath = path.resolve(entry.getKey());
				installDirectoryWatcher(fp, entrypath, watchers);
			}
		}
	}

	protected void reinstallAbandonedDirectoryWatcherLocked(SakerFileProvider fp, SakerPath path,
			ConcurrentNavigableMap<SakerPath, DirectoryWatcher> watchers, DirectoryWatcher watcher) {
		if (!watcher.configVersion.equals(this.configurationVersionKey)) {
			return;
		}
		installDirectoryWatcherLocked(fp, path, watchers, watcher);
	}

	protected void detectChangeLocked(SakerFileProvider fp, String filename, DirectoryWatcher watcher) {
		SakerPath path = watcher.path.resolve(filename);
		if (isPathExcluded(path)) {
			//do not install watcher for this directory or children
			return;
		}
		if (!watcher.configVersion.equals(this.configurationVersionKey)) {
			watcher.removeListener();
			return;
		}
		try {
			FileEntry attrs = fp.getFileAttributes(path);
			if (attrs.isDirectory()) {
				installDirectoryWatcher(fp, path, watcher.watchers);
			} else if (attrs.isRegularFile()) {
				//construct the model my getting it
				getModelLocked(path);
			}
		} catch (IOException | RMIRuntimeException e) {
			//file not found
			SpawnedModel gotmodel = models.remove(path);
			if (gotmodel != null) {
				gotmodel.close();
			}
		}
	}

	private void uninstallWatchers() {
		for (NavigableMap<SakerPath, DirectoryWatcher> watchedmap : watchers.values()) {
			for (DirectoryWatcher watcher : watchedmap.values()) {
				watcher.removeListener();
			}
		}
		watchers.clear();
	}

	@Override
	public void close() throws IOException {
		synchronized (this) {
			if (closed) {
				return;
			}
			closed = true;
			IOException exc = null;
			uninstallWatchers();
			IOUtils.closeExc(exc, accessorKeyModellingEngines.values());
			accessorKeyModellingEngines.clear();
			models.clear();
			IOUtils.throwExc(exc);
		}
	}

	@Override
	public NavigableSet<SakerPath> getTrackedScriptPaths() {
		synchronized (this) {
			if (closed) {
				return Collections.emptyNavigableSet();
			}
			return new TreeSet<>(models.navigableKeySet());
		}
	}

	@Override
	public ScriptSyntaxModel getModel(SakerPath scriptpath) {
		SakerPathFiles.requireAbsolutePath(scriptpath);
		synchronized (this) {
			if (closed) {
				return null;
			}
			SpawnedModel result = getModelLocked(scriptpath);
			if (result == null) {
				return null;
			}
			return result.model;
		}
	}

	private SpawnedModel getModelLocked(SakerPath scriptpath) {
		SpawnedModel m = models.get(scriptpath);
		if (m != null) {
			return m;
		}
		try {
			m = createModelForPath(scriptpath);
		} catch (Exception e) {
			//exception can be handled by subclasses by overriding the createModelForPath method 
			return null;
		}
		if (m == null) {
			return null;
		}
		models.put(scriptpath, m);
		return m;
	}

	//this method is protected, so it can be overridden, and the exception from this method can be handled by subclasses
	protected SpawnedModel createModelForPath(SakerPath scriptpath) throws Exception {
		ExecutionPathConfiguration pathconfig = configuration.getPathConfiguration();
		SakerFileProvider fp = pathconfig.getFileProviderIfPresent(scriptpath);
		if (fp == null) {
			return null;
		}
		if (isPathExcluded(scriptpath)) {
			//do not create model for files in exluded paths
			return null;
		}
		ScriptOptionsConfig pathscriptconfig = configuration.getScriptConfiguration()
				.getScriptOptionsConfig(scriptpath);
		if (pathscriptconfig == null) {
			return null;
		}
		if (!fp.getFileAttributes(scriptpath).isRegularFile()) {
			//don't create model for files which don't exist, as it can result in invalid files being in the model
			return null;
		}
		ScriptAccessorClassPathData scriptclasspathdata;
		ScriptProviderLocation scriptproviderlocator = pathscriptconfig.getProviderLocation();
		SakerEnvironmentImpl environment = this.environment;
		scriptclasspathdata = environment.getCachedData(
				new ScriptAccessorClassPathCacheKey(scriptproviderlocator, environment.getClassPathManager()));
		ScriptAccessProvider accessor = scriptclasspathdata.getScriptAccessor();
		Object accessorkey = accessor.getScriptAccessorKey();
		SpawnedEngine spawnedengine = accessorKeyModellingEngines.get(accessorkey);
		if (spawnedengine == null) {
			ScriptModellingEngine createdmodellingengine = accessor.createModellingEngine(this);
			spawnedengine = new SpawnedEngine(createdmodellingengine);
			accessorKeyModellingEngines.put(accessorkey, spawnedengine);
		}
		return new SpawnedModel(spawnedengine.engine, spawnedengine.createModel(
				new SimpleScriptParsingOptions(scriptpath, pathscriptconfig.getOptions()), fp, scriptpath));
	}

	protected final static class SpawnedEngine implements Closeable {
		protected ScriptModellingEngine engine;
		protected final ConcurrentPrependAccumulator<ScriptSyntaxModel> createdModels = new ConcurrentPrependAccumulator<>();

		public SpawnedEngine(ScriptModellingEngine engine) {
			this.engine = engine;
		}

		public ScriptSyntaxModel createModel(ScriptParsingOptions parsingoptions, SakerFileProvider fp,
				SakerPath path) {
			ScriptSyntaxModel result = engine.createModel(parsingoptions, () -> fp.openInput(path));
			createdModels.add(result);
			return result;
		}

		@Override
		public void close() throws IOException {
			ScriptSyntaxModel m;
			while ((m = createdModels.take()) != null) {
				engine.destroyModel(m);
			}
			engine.close();
		}
	}

	protected final static class SpawnedModel implements Closeable {
		protected ScriptModellingEngine engine;
		protected ScriptSyntaxModel model;

		public SpawnedModel(ScriptModellingEngine engine, ScriptSyntaxModel model) {
			this.engine = engine;
			this.model = model;
		}

		@Override
		public void close() {
			engine.destroyModel(model);
		}

	}

	private static class DirectoryWatcher implements FileEventListener {
		private static final AtomicReferenceFieldUpdater<BasicScriptModellingEnvironment.DirectoryWatcher, FileEventListener.ListenerToken> ARFU_token = AtomicReferenceFieldUpdater
				.newUpdater(BasicScriptModellingEnvironment.DirectoryWatcher.class,
						FileEventListener.ListenerToken.class, "token");

		private volatile FileEventListener.ListenerToken token;
		private SakerFileProvider fileProvider;
		private SakerPath path;
		private ConcurrentNavigableMap<SakerPath, DirectoryWatcher> watchers;
		protected Object configVersion;
		private BasicScriptModellingEnvironment environment;

		public DirectoryWatcher(BasicScriptModellingEnvironment env, SakerFileProvider fileProvider, SakerPath path,
				ConcurrentNavigableMap<SakerPath, DirectoryWatcher> watchers, Object configVersion) {
			this.environment = env;
			this.fileProvider = fileProvider;
			this.path = path;
			this.watchers = watchers;
			this.configVersion = configVersion;
		}

		public synchronized void initToken(FileEventListener.ListenerToken token) {
			if (!ARFU_token.compareAndSet(this, null, token)) {
				throw new IllegalStateException();
			}
		}

		public void removeListener() {
			FileEventListener.ListenerToken t = ARFU_token.getAndSet(this, null);
			removeListenerImpl(t);
		}

		private void removeListenerImpl(FileEventListener.ListenerToken t) {
			if (t == null) {
				return;
			}
			watchers.remove(path, this);
			try {
				t.removeListener();
			} catch (RMIRuntimeException e) {
				//ignored exception
				//no need to handle, the listener will be garbage collected automatically after a while.
			}
		}

		@Override
		public void changed(String filename) {
			synchronized (environment) {
				synchronized (this) {
					//this check needs to be in synchronized, as if we're not yet initialized, we still want to examine the event
					if (token == null) {
						return;
					}
					environment.detectChangeLocked(fileProvider, filename, this);
				}
			}
		}

		@Override
		public void listenerAbandoned() {
			FileEventListener.ListenerToken t = ARFU_token.getAndSet(this, null);
			if (t == null) {
				return;
			}
			synchronized (environment) {
				synchronized (this) {
					removeListenerImpl(t);
					environment.reinstallAbandonedDirectoryWatcherLocked(fileProvider, path, watchers, this);
				}
			}
		}

		@Override
		public void eventsMissed() {
			FileEventListener.ListenerToken t = ARFU_token.getAndSet(this, null);
			if (t == null) {
				return;
			}
			synchronized (environment) {
				synchronized (this) {
					removeListenerImpl(t);
					environment.reinstallAbandonedDirectoryWatcherLocked(fileProvider, path, watchers, this);
				}
			}
		}
	}
}
