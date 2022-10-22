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
package saker.build.runtime.project;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

import saker.build.file.MarkerSakerDirectory;
import saker.build.file.ProviderPathSakerDirectory;
import saker.build.file.ProviderPathSakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDatabaseImpl;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileEventListener;
import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.FileContentDataComputeHandler;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import testing.saker.build.flag.TestFlag;

public class ProjectFileChangesWatchHandler implements Closeable {
	private final NavigableMap<SakerPath, ProviderPathSakerDirectory> rootDirectoryCache = new ConcurrentSkipListMap<>();

	private final Map<SakerFileProvider, ConcurrentNavigableMap<SakerPath, FileEventListener.ListenerToken>> listeners = new IdentityHashMap<>();
	private final Map<RootFileProviderKey, NavigableSet<SakerPath>> pathsToRecheck = new ConcurrentHashMap<>();
	private final Map<RootFileProviderKey, NavigableSet<SakerPath>> watchedPaths = new ConcurrentHashMap<>();

	private volatile boolean closed = false;

	private ContentDatabaseImpl currentDatabase;

	public void watch(ContentDatabaseImpl database, Map<String, ? extends SakerDirectory> rootdirs,
			FileContentDataComputeHandler filecomputedatahandler) {
		synchronized (ProjectFileChangesWatchHandler.this) {
			checkClosed();
			checkStopped();
			this.currentDatabase = database;
			rootDirectoryCache.clear();
			ConcurrentHashMap<RootFileProviderKey, ConcurrentSkipListSet<SakerPath>> dbinvalidateddirectorypaths = new ConcurrentHashMap<>();
			if (!ObjectUtils.isNullOrEmpty(rootdirs)) {
				try (ThreadWorkPool pool = ThreadUtils.newFixedWorkPool("Watcher installer-")) {
					for (Entry<String, ? extends SakerDirectory> entry : rootdirs.entrySet()) {
						SakerDirectory rd = entry.getValue();
						pool.offer(() -> installWatchers(rd, SakerPath.valueOf(entry.getKey()), pool, database,
								filecomputedatahandler, dbinvalidateddirectorypaths));
					}
					pool.reset();
				}
			}
			ExecutionPathConfiguration pathconfig = database.getPathConfiguration();
			for (RootFileProviderKey fpkey : database.getTrackedFileProviderKeys()) {
				SakerFileProvider fp = pathconfig.getFileProviderIfPresent(fpkey);
				if (fp == null) {
					if (fpkey.equals(LocalFileProvider.getProviderKeyStatic())) {
						fp = LocalFileProvider.getInstance();
					} else {
						continue;
					}
				}
				NavigableSet<SakerPath> trackedpaths = database.getTrackedFilePaths(fpkey);
				if (trackedpaths.isEmpty()) {
					continue;
				}
				NavigableSet<SakerPath> watchedpaths = getWatchedPathsSet(fpkey);
				NavigableSet<SakerPath> recheckerpathset = getRecheckerPathsSet(fpkey);
				Set<SakerPath> dbinvalidatedfpdirpaths = dbinvalidateddirectorypaths.get(fpkey);
				if (dbinvalidatedfpdirpaths == null) {
					dbinvalidatedfpdirpaths = Collections.emptyNavigableSet();
				}
				for (SakerPath p : trackedpaths) {
					SakerPath parent = p.getParent();
					if (parent == null) {
						//shouldn't really ever happen, as the tracked paths should have at least one path names
						continue;
					}
					if (watchedpaths.contains(parent)) {
						//parent is already being watched. 
						//we still need to invalidate the entry in the database
						//but only if we haven't invalidated the entries of the directory during watching
						if (!dbinvalidatedfpdirpaths.contains(parent)) {
							try {
								FileEntry attrs = fp.getFileAttributes(p);
								database.invalidateEntryOffline(fpkey, p, attrs);
							} catch (IOException e) {
								//failed to read the attributes of a tracked file in the database
								database.invalidateHardOffline(fpkey, p);
								recheckerpathset.add(p);
								//XXX do we need to print this?
								e.printStackTrace();
							}
						}
						//continue, dont add watcher
						continue;
					}

					//the parent path is not yet watched, watch it.

					NavigableMap<SakerPath, FileEventListener.ListenerToken> listeners = this.listeners
							.computeIfAbsent(fp, Functionals.concurrentSkipListMapComputer());
					NavigableSet<SakerPath> dirdatabasetrackedpaths = SakerPathFiles
							.getPathSubSetDirectoryChildren(trackedpaths, parent, true);
					NonSakerDirectoryChangeHandleFileEventListener listener = new NonSakerDirectoryChangeHandleFileEventListener(
							fpkey, fp, parent, l -> {
								listeners.remove(parent, l);
								watchedpaths.remove(parent);
							}, dirdatabasetrackedpaths, recheckerpathset, database);
					try {
						ListenerToken ltoken;
						synchronized (listener.lock) {
							ltoken = fp.addFileEventListener(parent, listener);
							listener.listenerToken = ltoken;

							try {
								FileEntry attrs = fp.getFileAttributes(p);
								database.invalidateEntryOffline(fpkey, p, attrs);
							} catch (IOException e) {
								//failed to read the attributes of a tracked file in the database
								database.invalidateHardOffline(fpkey, p);
								recheckerpathset.add(p);
								//XXX do we need to print this?
								e.printStackTrace();
								//NO continue. we need to finish the listener installing
							}
						}
						watchedpaths.add(parent);
						FileEventListener.ListenerToken prev = listeners.putIfAbsent(parent, ltoken);
						if (prev != null) {
							//internal error, the listener was installed multiple times
							//should NEVER happen, but handle it by removing the listener nonetheless
							ltoken.removeListener();
							if (TestFlag.ENABLED) {
								//fail during testing, recover in production
								throw new AssertionError();
							}
						}
					} catch (IOException e) {
						//failed to add the file listener to the parent directory
						//we don't hard fail with the content database, as the given file provider might not support file watching
						//    print the exception nonetheless
						e.printStackTrace();
						recheckerpathset.addAll(dirdatabasetrackedpaths);
						database.invalidateOffline(fpkey, parent);
					}
				}
			}
		}
	}

	public ContentDatabaseImpl getCurrentDatabase() {
		return currentDatabase;
	}

	public void stopIfWatching() {
		synchronized (ProjectFileChangesWatchHandler.this) {
			checkClosed();
			if (this.currentDatabase != null) {
				stopImpl();
			}
		}
	}

	public void stopClearCachedDirectories() {
		synchronized (ProjectFileChangesWatchHandler.this) {
			checkClosed();
			checkWatching();
			stopImpl();
			this.rootDirectoryCache.clear();
		}
	}

	public void stopRecheckPathsOpt() {
		synchronized (ProjectFileChangesWatchHandler.this) {
			checkClosed();
			checkWatching();
			this.currentDatabase.recheckContentChangesOffline(pathsToRecheck);
			stopImpl();
		}
	}

	public ProviderPathSakerDirectory getCachedRootDirectory(SakerPath rootsakerpath) {
		return rootDirectoryCache.get(rootsakerpath);
	}

	@Override
	public void close() throws IOException {
		closed = true;
		synchronized (ProjectFileChangesWatchHandler.this) {
			if (this.currentDatabase != null) {
				stopImpl();
			}
			rootDirectoryCache.clear();
		}
	}

	private void stopImpl() {
		uninstallWatchers();
		pathsToRecheck.clear();
		this.currentDatabase = null;
	}

	private void checkClosed() {
		if (closed) {
			throw new IllegalStateException("Closed.");
		}
	}

	private void checkWatching() {
		if (this.currentDatabase == null) {
			throw new IllegalStateException("Not watching any database.");
		}
	}

	private void checkStopped() {
		if (this.currentDatabase != null) {
			throw new IllegalStateException("Already watching a database.");
		}
	}

	private void uninstallWatchers() {
		watchedPaths.clear();

		//XXX maybe parallelize this
		for (Entry<SakerFileProvider, ? extends NavigableMap<SakerPath, FileEventListener.ListenerToken>> entry : listeners
				.entrySet()) {
			NavigableMap<SakerPath, ListenerToken> listeners = entry.getValue();
			if (ObjectUtils.isNullOrEmpty(listeners)) {
				//no listeners, nothing to remove, continue to avoid RMI call
				continue;
			}
			SakerFileProvider fp = entry.getKey();
			fp.removeFileEventListeners(listeners.values());
		}
		listeners.clear();
	}

	private void installWatchers(SakerDirectory rootdirectory, SakerPath rootdirpath, ThreadWorkPool pool,
			ContentDatabaseImpl database, FileContentDataComputeHandler filecomputedatahandler,
			ConcurrentHashMap<RootFileProviderKey, ConcurrentSkipListSet<SakerPath>> dbinvalidateddirectorypaths) {
		if (!(rootdirectory instanceof ProviderPathSakerDirectory)) {
			SakerLog.error().println("Root directory is not an instance of "
					+ ProviderPathSakerDirectory.class.getName() + " for path: " + rootdirectory.getSakerPath());
			return;
		}
		ProviderPathSakerDirectory pdir = (ProviderPathSakerDirectory) rootdirectory;

//		SakerPath mpath = pdir.getRealSakerPath();
//		SakerFileProvider fp = pdir.getFileProvider();
		ProviderHolderPathKey pathkey = SakerPathFiles.getPathKey(pdir.getFileProvider(), pdir.getRealSakerPath());
		SakerPath mpath = pathkey.getPath();
		SakerFileProvider fp = pathkey.getFileProvider();
		RootFileProviderKey fpkey = pathkey.getFileProviderKey();

		NavigableMap<SakerPath, FileEventListener.ListenerToken> containingmap;
		NavigableSet<SakerPath> watchedpathsset = getWatchedPathsSet(fpkey);
		synchronized (listeners) {
			containingmap = listeners.computeIfAbsent(fp, Functionals.concurrentSkipListMapComputer());
		}
		NavigableSet<SakerPath> dirdatabasetrackedfiles;
		if (database == null) {
			dirdatabasetrackedfiles = Collections.emptyNavigableSet();
		} else {
			dirdatabasetrackedfiles = SakerPathFiles.getPathSubSetDirectoryChildren(database.getTrackedFilePaths(fpkey),
					mpath, true);
		}
		NavigableSet<SakerPath> recheckerpathset = getRecheckerPathsSet(fpkey);
		if (installWatchersImpl(pdir, mpath, fp, fpkey, pool, containingmap, watchedpathsset, dirdatabasetrackedfiles,
				recheckerpathset, database, filecomputedatahandler,
				dbinvalidateddirectorypaths.computeIfAbsent(fpkey, Functionals.concurrentSkipListSetComputer()))) {
			rootDirectoryCache.put(rootdirpath, pdir);
		}
	}

	private boolean installWatchersImpl(ProviderPathSakerDirectory pdir, SakerPath mpath, SakerFileProvider fp,
			RootFileProviderKey fpkey, ThreadWorkPool pool,
			NavigableMap<SakerPath, ? super FileEventListener.ListenerToken> containingmap,
			NavigableSet<SakerPath> watchedpathsset, NavigableSet<SakerPath> dirdatabasetrackedfiles,
			NavigableSet<SakerPath> recheckerpathset, ContentDatabaseImpl database,
			FileContentDataComputeHandler filecomputedatahandler,
			ConcurrentSkipListSet<SakerPath> dbinvalidateddirectorypaths) {
		if (closed) {
			return false;
		}
		try {
			//only install listener if we have any populated file
			NavigableMap<String, ? extends SakerFile> currentlypopulatedfiles = pdir.getTrackedFilesMap();
			//we add the listeners to all of the directories, as one might query a file that doesnt exist, and depend on its addition
			//XXX we might implement a query tracking so that only directories are listened for which are actually visited
			if (pdir.isAnyPopulated() || !currentlypopulatedfiles.isEmpty()) {
				ChangeHandleFileEventListener listener = new ChangeHandleFileEventListener(fp, mpath, fpkey, pdir,
						watchedpathsset, containingmap, dirdatabasetrackedfiles, recheckerpathset,
						filecomputedatahandler, database);
				synchronized (listener.lock) {
					ListenerToken ltoken;
					try {
						ltoken = fp.addFileEventListener(mpath, listener);
						listener.listenerToken = ltoken;
					} catch (IOException e) {
						if (database != null) {
							database.invalidateOffline(fpkey, mpath);
						}
						recheckerpathset.addAll(dirdatabasetrackedfiles);
						throw e; //caught by the enclosing try-catch
					}
					NavigableMap<String, ? extends BasicFileAttributes> repaired = pdir.repair();
					if (repaired == null) {
						if (database != null) {
							database.invalidateOffline(fpkey, mpath);
						}
						recheckerpathset.addAll(dirdatabasetrackedfiles);
						return false;
					}
					if (database != null) {
						database.invalidateEntriesOffline(fpkey, mpath, repaired);
						dbinvalidateddirectorypaths.add(mpath);
					}
					containingmap.put(mpath, ltoken);
				}

				watchedpathsset.add(mpath);

				Map<String, ? extends SakerFile> repairedpopulatedfiles = pdir.getTrackedFilesMap();

				for (Entry<String, ? extends SakerFile> entry : repairedpopulatedfiles.entrySet()) {
					SakerFile subfile = entry.getValue();
					if (subfile.getClass() == ProviderPathSakerDirectory.class) {
						pool.offer(() -> {
							String subfilename = subfile.getName();
							SakerPath subfilepath = mpath.resolve(subfilename);
							boolean subinstalled = installWatchersImpl((ProviderPathSakerDirectory) subfile,
									subfilepath, fp, fpkey, pool, containingmap, watchedpathsset,
									SakerPathFiles.getPathSubSetDirectoryChildren(dirdatabasetrackedfiles, subfilepath,
											true),
									recheckerpathset, database, filecomputedatahandler, dbinvalidateddirectorypaths);
							if (!subinstalled) {
								//failed to install listener on sub directory
								//this probably means that the subdirectory was removed or not available
								//remove it from the files
								repairedpopulatedfiles.remove(subfilename, subfile);
							}
						});
					} else if (subfile.getClass() != ProviderPathSakerFile.class) {
						throw new AssertionError("Unknown repaired file class: " + subfile.getClass());
					}
				}
			}
			//else not populated, and the directory contains no files, and no files were referenced under it
			return true;
		} catch (IOException e) {
			//failed to install listener for the directory
			e.printStackTrace();
		}
		if (database != null) {
			database.invalidateOffline(fpkey, mpath);
		}
		if (filecomputedatahandler != null) {
			filecomputedatahandler.invalidate(mpath);
		}
		recheckerpathset.addAll(dirdatabasetrackedfiles);
		return false;
	}

	private NavigableSet<SakerPath> getWatchedPathsSet(RootFileProviderKey fpkey) {
		return watchedPaths.computeIfAbsent(fpkey, Functionals.concurrentSkipListSetComputer());
	}

	private NavigableSet<SakerPath> getRecheckerPathsSet(RootFileProviderKey fpkey) {
		return pathsToRecheck.computeIfAbsent(fpkey, Functionals.concurrentSkipListSetComputer());
	}

	private static class DatabaseDirectoryRecheckPathAdderFileEventLister implements FileEventListener {
		protected SakerPath sakerPath;
		protected NavigableSet<SakerPath> directoryDatabaseTrackedFiles;
		protected NavigableSet<SakerPath> databaseRecheckPathsSet;

		public DatabaseDirectoryRecheckPathAdderFileEventLister(SakerPath sakerPath,
				NavigableSet<SakerPath> directoryDatabaseTrackedFiles,
				NavigableSet<SakerPath> databaseRecheckPathsSet) {
			this.sakerPath = sakerPath;
			this.directoryDatabaseTrackedFiles = directoryDatabaseTrackedFiles;
			this.databaseRecheckPathsSet = databaseRecheckPathsSet;
		}

		@Override
		public void changed(String filename) {
			SakerPath fmpath = sakerPath.resolve(filename);
			databaseRecheckPathsSet
					.addAll(SakerPathFiles.getPathSubSetDirectoryChildren(directoryDatabaseTrackedFiles, fmpath, true));
		}

		@Override
		public void eventsMissed() {
			databaseRecheckPathsSet.addAll(directoryDatabaseTrackedFiles);
		}

		@Override
		public void listenerAbandoned() {
			databaseRecheckPathsSet.addAll(directoryDatabaseTrackedFiles);
		}
	}

	private static class ChangeHandleFileEventListener extends DatabaseDirectoryRecheckPathAdderFileEventLister {
		protected RootFileProviderKey providerKey;
		protected SakerFileProvider fileProvider;
		protected ProviderPathSakerDirectory directory;

		protected Map<SakerPath, ?> containingMap;
		protected Set<SakerPath> watchedContainerSet;

		protected FileEventListener.ListenerToken listenerToken;
		protected FileContentDataComputeHandler fileComputeHandler;
		protected ContentDatabaseImpl database;

		protected final Object lock = this;

		public ChangeHandleFileEventListener(SakerFileProvider fileProvider, SakerPath path,
				RootFileProviderKey providerKey, ProviderPathSakerDirectory directory,
				Set<SakerPath> watchedContainerSet, Map<SakerPath, ?> containingMap,
				NavigableSet<SakerPath> dirdatabasetrackedfiles, NavigableSet<SakerPath> recheckerpathset,
				FileContentDataComputeHandler filecomputehandler, ContentDatabaseImpl database) {
			super(path, dirdatabasetrackedfiles, recheckerpathset);
			this.fileProvider = fileProvider;
			this.providerKey = providerKey;
			this.directory = directory;
			this.watchedContainerSet = watchedContainerSet;
			this.containingMap = containingMap;
			this.fileComputeHandler = filecomputehandler;
			this.database = database;
		}

		@Override
		public void changed(String filename) {
			super.changed(filename);
			SakerPath path = sakerPath.resolve(filename);
			if (fileComputeHandler != null) {
				fileComputeHandler.invalidate(path);
			}

			if (database != null) {
				database.invalidateOffline(providerKey, path);
			}
			//XXX Note: slight code duplication below
			if (directory.isAnyPopulated()) {
				synchronized (lock) {
					ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = directory.getTrackedFilesMap();
					try {
						FileEntry attrs = fileProvider.getFileAttributes(path);
						if (attrs.isDirectory()) {
							SakerFile presentfile = trackedfiles.get(filename);
							if (presentfile instanceof SakerDirectory
									&& !(presentfile instanceof MarkerSakerDirectory)) {
								//a directory is already present there
								//changes to a directory contents will be applied when the appropriate listener is called
							} else {
								//add it as a directory
								directory.addPopulatedDirectory(filename);
							}
						} else {
							directory.addPopulatedFile(filename);
						}
					} catch (IOException e) {
						//failed to get the attributes for the file, consider it deleted
						trackedfiles.remove(filename);
					}
				}
			} else {
				synchronized (lock) {
					ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = directory.getTrackedFilesMap();
					SakerFile presentfile = trackedfiles.get(filename);
					if (presentfile == null) {
						//no file is present for this name, nothing was populated, okay
						//no need to deal with this change
					} else {
						try {
							FileEntry attrs = fileProvider.getFileAttributes(path);
							if (attrs.isDirectory()) {
								if (presentfile instanceof SakerDirectory
										&& !(presentfile instanceof MarkerSakerDirectory)) {
									//a directory is already present there
									//changes to a directory contents will be applied when the appropriate listener is called
								} else {
									//add it as a directory
									directory.addPopulatedDirectory(filename);
								}
							} else {
								directory.addPopulatedFile(filename);
							}
						} catch (IOException e) {
							//failed to get the attributes for the file, consider it deleted
							trackedfiles.remove(filename, presentfile);
						}
					}
				}
			}
		}

		@Override
		public void eventsMissed() {
			super.eventsMissed();

			synchronized (lock) {
				ListenerToken lt = listenerToken;
				if (lt != null) {
					lt.removeListener();
				}
				error();
			}
		}

		@Override
		public void listenerAbandoned() {
			super.listenerAbandoned();

			synchronized (lock) {
				error();
			}
		}

		private void error() {
			directory.clear();
			directory.clearPopulated();
			watchedContainerSet.remove(sakerPath);
			containingMap.remove(sakerPath, this);
		}
	}

	private static class NonSakerDirectoryChangeHandleFileEventListener
			extends DatabaseDirectoryRecheckPathAdderFileEventLister {
		protected final RootFileProviderKey providerKey;
		protected final SakerFileProvider fileProvider;

		protected Consumer<? super NonSakerDirectoryChangeHandleFileEventListener> mapRemover;

		protected FileEventListener.ListenerToken listenerToken;
		protected ContentDatabaseImpl database;

		protected final Object lock = this;

		public NonSakerDirectoryChangeHandleFileEventListener(RootFileProviderKey providerKey,
				SakerFileProvider fileProvider, SakerPath sakerPath,
				Consumer<? super NonSakerDirectoryChangeHandleFileEventListener> mapRemover,
				NavigableSet<SakerPath> dirdatabasetrackedfiles, NavigableSet<SakerPath> recheckerpathset,
				ContentDatabaseImpl database) {
			super(sakerPath, dirdatabasetrackedfiles, recheckerpathset);
			this.providerKey = providerKey;
			this.fileProvider = fileProvider;
			this.mapRemover = mapRemover;
			this.database = database;
		}

		@Override
		public void changed(String filename) {
			super.changed(filename);
			database.invalidateOffline(providerKey, sakerPath.resolve(filename));
		}

		@Override
		public void eventsMissed() {
			super.eventsMissed();

			synchronized (lock) {
				error();
				listenerToken.removeListener();
			}
		}

		@Override
		public void listenerAbandoned() {
			super.listenerAbandoned();

			synchronized (lock) {
				error();
			}
		}

		private void error() {
			mapRemover.accept(this);
		}
	}
}
