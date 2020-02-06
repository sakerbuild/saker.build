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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import saker.build.cache.BuildCacheAccessor;
import saker.build.file.ProviderPathSakerDirectory;
import saker.build.file.SakerDirectory;
import saker.build.file.content.ContentDatabaseImpl;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.DirectoryMountFileProvider;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileLock;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.runtime.execution.ExecutionParameters;
import saker.build.runtime.execution.ExecutionProgressMonitor;
import saker.build.runtime.execution.FileContentDataComputeHandler;
import saker.build.runtime.execution.FileMirrorHandler;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.task.TaskExecutionResult;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.trace.InternalBuildTrace;
import saker.build.util.cache.MemoryTrimmer;

public class SakerProjectCache implements ProjectCacheHandle {
	private static final String LOCK_FILE_NAME = ".saker.project.lock";

	public static class ProjectExecutionSetupCancelledException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ProjectExecutionSetupCancelledException() {
		}

		public ProjectExecutionSetupCancelledException(Throwable cause) {
			super(cause);
		}

		public ProjectExecutionSetupCancelledException(String message, Throwable cause) {
			super(message, cause);
		}

		public ProjectExecutionSetupCancelledException(String message) {
			super(message);
		}

	}

	private final SakerEnvironmentImpl environment;
	private final Semaphore usageLock = new Semaphore(1);

	private volatile Thread executionFinalizerThread;
	private volatile boolean startingRequested = false;
	private volatile boolean closed = false;

	private boolean fileWatchingEnabled = true;

	private SakerExecutionCache executionCache;

	private ProjectFileChangesWatchHandler executionFileWatchHandler;
	private ContentDatabaseImpl executionDatabase;
	private SakerFileLock executionWorkingDirectoryFileLock;
	private FileContentDataComputeHandler cachedExecutionFileContentDataComputeHandler;

	private ContentDatabaseImpl clusterDatabase;
	private ProjectFileChangesWatchHandler clusterFileWatchHandler;
	private FileMirrorHandler clusterMirrorHandler;

	public SakerProjectCache(SakerEnvironmentImpl environment) {
		this.environment = environment;
		this.executionCache = new SakerExecutionCache(environment);
	}

	public SakerExecutionCache getExecutionCache() {
		return executionCache;
	}

	public FileContentDataComputeHandler getCachedFileContentDataComputeHandler() {
		return cachedExecutionFileContentDataComputeHandler;
	}

	public FileMirrorHandler getClusterMirrorHandler() {
		return clusterMirrorHandler;
	}

	public ContentDatabaseImpl getClusterContentDatabase() {
		return clusterDatabase;
	}

	public void waitExecutionFinalization() throws InterruptedException {
		waitExecutionFinalizerThread(false);
	}

	public void setFileWatchingEnabled(boolean fileWatchingEnabled) {
		this.fileWatchingEnabled = fileWatchingEnabled;
	}

	public ContentDatabaseImpl getExecutionContentDatabase() {
		return executionDatabase;
	}

	public boolean isClosed() {
		return closed;
	}

	public ProviderPathSakerDirectory getCachedRootDirectory(SakerPath rootsakerpath, SakerFileProvider fp) {
		if (closed || executionFileWatchHandler == null) {
			return null;
		}

		ProviderPathSakerDirectory cached = executionFileWatchHandler.getCachedRootDirectory(rootsakerpath);
		if (cached == null) {
			return null;
		}
		if (!SakerPathFiles.isSameProvider(cached.getFileProvider(), fp)) {
			return null;
		}
		return cached;
	}

	@Override
	public void reset() {
		try {
			usageLock.acquireUninterruptibly();
			waitExecutionFinalizerThreadNonInterruptible(true);
			this.cachedExecutionFileContentDataComputeHandler = null;

			IOUtils.closePrint(executionFileWatchHandler);
			executionFileWatchHandler = null;

			IOUtils.closePrint(clusterFileWatchHandler);
			clusterFileWatchHandler = null;
			this.clusterMirrorHandler = null;

			if (executionDatabase != null) {
				IOUtils.closePrint(executionDatabase);
				executionDatabase = null;
			}
			if (clusterDatabase != null) {
				IOUtils.closePrint(clusterDatabase);
				clusterDatabase = null;
			}
			IOUtils.closePrint(executionCache::clear);
		} finally {
			usageLock.release();
		}
	}

	@Override
	public SakerProjectCache toProject() {
		return this;
	}

	@Override
	public void clean() {
		try {
			usageLock.acquireUninterruptibly();
			waitExecutionFinalizerThreadNonInterruptible(true);
			this.cachedExecutionFileContentDataComputeHandler = null;

			IOUtils.closePrint(executionFileWatchHandler);
			executionFileWatchHandler = null;
			IOUtils.closePrint(clusterFileWatchHandler);
			clusterFileWatchHandler = null;

			if (executionDatabase != null) {
				executionDatabase.clean();
				executionDatabase = null;
			}
			if (clusterDatabase != null) {
				clusterDatabase.clean();
				clusterDatabase = null;
			}
			IOUtils.closePrint(executionCache::clear);
		} finally {
			usageLock.release();
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (this) {
			if (closed) {
				return;
			}
			closed = true;
		}
		usageLock.acquireUninterruptibly();
		try {
			waitExecutionFinalizerThreadNonInterruptible(false);
			this.cachedExecutionFileContentDataComputeHandler = null;
			IOException exc = null;

			exc = IOUtils.closeExc(exc, this.executionFileWatchHandler, this.clusterFileWatchHandler);
			executionFileWatchHandler = null;
			clusterFileWatchHandler = null;

			exc = IOUtils.closeExc(exc, this.executionDatabase, this.clusterDatabase);
			exc = IOUtils.closeExc(exc, this.executionCache);
			exc = IOUtils.closeExc(exc, this.executionWorkingDirectoryFileLock);
			IOUtils.throwExc(exc);
		} finally {
			usageLock.release();
		}
	}

	//XXX examine clusterStarting and executionStarting methods (and finishing methods too) and extract common parts if possible

	public void clusterStarting(ExecutionPathConfiguration pathconfig, ExecutionRepositoryConfiguration repoconfig,
			ExecutionScriptConfiguration scriptconfig, Map<String, String> userparams, Path clustermirrordirectory,
			RootFileProviderKey coordinatorproviderkey, DatabaseConfiguration databaseconfig,
			ExecutionContext executioncontext) throws Exception {
		startingRequested = true;

		SakerPath workingdir = pathconfig.getWorkingDirectory();
		Thread currentthread = Thread.currentThread();
		lockUsageLockForExecution(new ExecutionProgressMonitor.NullProgressMonitor(), workingdir, currentthread);

		Semaphore execlocktounlock = usageLock;
		try {
			//wait the finalizer thread without interruption so the database is flushed properly
			//   so the next time it is read with newly loaded repositories, the data won't be corrupted
			waitExecutionFinalizerThread(false);

			if (clusterMirrorHandler != null
					&& !clusterMirrorHandler.getMirrorDirectory().equals(clustermirrordirectory)) {
				IOUtils.closePrint(this.clusterDatabase);
				this.clusterDatabase = null;
				clusterFileWatchHandler.close();
				clusterFileWatchHandler = null;
				clusterMirrorHandler = null;
			}
			//TODO handle the exception from the following .set call
			boolean executioncachechanged = executionCache.set(pathconfig, repoconfig, scriptconfig, userparams,
					coordinatorproviderkey);
			if (executioncachechanged) {
				clearExecutionCacheChangeRelatedResources();
			}

			if (clustermirrordirectory != null) {
				ExecutionPathConfiguration.Builder modifiedpathconfigbuilder = ExecutionPathConfiguration
						.builder(pathconfig.getWorkingDirectory());
				SakerPath mirrordirspath = SakerPath.valueOf(clustermirrordirectory);
				LocalFileProvider localfiles = LocalFileProvider.getInstance();

				for (Entry<String, SakerFileProvider> entry : pathconfig.getRootFileProviders().entrySet()) {
					String rootname = entry.getKey();
					SakerPath mountpath = mirrordirspath.resolve(FileMirrorHandler.normalizeRootName(rootname));
					modifiedpathconfigbuilder.addRootProvider(rootname,
							DirectoryMountFileProvider.create(localfiles, mountpath, rootname));
				}
				ExecutionPathConfiguration clustermodifiedpathconfig = modifiedpathconfigbuilder.build();
				Path clusterdbpath = clustermirrordirectory
						.resolve("clusterdb" + ContentDatabaseImpl.DATABASE_EXTENSION);
				ProviderHolderPathKey clusterdbpathkey = LocalFileProvider.getInstance().getPathKey(clusterdbpath);
				if (clusterDatabase == null
						|| !clusterDatabase.isConfiguredTo(clustermodifiedpathconfig, clusterdbpathkey, databaseconfig)
						|| !clusterDatabase.isDatabaseFileExists()) {
					//close if configuration change
					IOUtils.closePrint(this.clusterDatabase);
					clusterDatabase = new ContentDatabaseImpl(databaseconfig, clustermodifiedpathconfig,
							executionCache.getExecutionClassLoaderResolver(), clusterdbpathkey);
					clusterDatabase.setTrackHandleAttributes(true);
				}
				if (clusterMirrorHandler == null) {
					clusterMirrorHandler = new FileMirrorHandler(clustermirrordirectory, clustermodifiedpathconfig,
							clusterDatabase, executioncontext);
				}
			}
			if (clusterFileWatchHandler != null) {
				ContentDatabaseImpl watcheddb = clusterFileWatchHandler.getCurrentDatabase();
				if (watcheddb != null) {
					if (watcheddb == this.clusterDatabase) {
						clusterFileWatchHandler.stopRecheckPathsOpt();
					} else {
						//we need to clear the cached directories, as they are bound to different database
						clusterFileWatchHandler.stopClearCachedDirectories();
					}
				}
			}

			execlocktounlock = null;
		} catch (InterruptedException e) {
			throw new ProjectExecutionSetupCancelledException(e);
		} finally {
			if (execlocktounlock != null) {
				execlocktounlock.release();
			}
		}
	}

	public void clusterFinished(Object environmentexecutionkey) {
		final Object fenvironmentexecutionkey = environmentexecutionkey;
		try {
			if (this.executionFinalizerThread != null) {
				throw new IllegalStateException("Cluster use was not started.");
			}
			startingRequested = false;

			if (this.clusterDatabase != null) {
				if (!this.clusterDatabase.isPersisting()) {
					//just release the database, as it is not saved to any path anyway
					this.clusterDatabase = null;
				} else {
					ThreadGroup tg = new ThreadGroup(this.environment.getEnvironmentThreadGroup(), "Cluster finalizer");
					tg.setDaemon(true);
					executionFinalizerThread = new Thread(tg, () -> {
						try {
							runClusterFinisherThreadImpl();
						} catch (Exception e) {
							e.printStackTrace();
							throw e;
						} finally {
							environment.executionFinished(fenvironmentexecutionkey);
						}
					}, "Cluster finalizer");
					//the finalizer thread is not daemon, as we need to flush the database before exiting the VM
					executionFinalizerThread.start();
					environmentexecutionkey = null;
				}
			}
		} finally {
			if (environmentexecutionkey != null) {
				environment.executionFinished(environmentexecutionkey);
			}
			usageLock.release();
		}
	}

	public void executionStarting(ExecutionProgressMonitor monitor, SakerPath builddirectory,
			ExecutionParameters parameters) throws Exception {
		startingRequested = true;
		ExecutionPathConfiguration pathconfig = parameters.getPathConfiguration();
		SakerPath workingdir = pathconfig.getWorkingDirectory();

		Thread currentthread = Thread.currentThread();

		lockUsageLockForExecution(monitor, workingdir, currentthread);

		Semaphore execlocktounlock = usageLock;
		SakerFileLock filetounlock = null;
		try {
			checkExecutionSetupCancelled(monitor, currentthread);

			SakerPath lockfile = workingdir.resolve(LOCK_FILE_NAME);
			try {
				executionWorkingDirectoryFileLock = pathconfig.getFileProvider(lockfile).createLockFile(lockfile);
				boolean filelocked = executionWorkingDirectoryFileLock.tryLock();
				if (!filelocked) {
					SakerLog.log().verbose().println("Awaiting directory lock for project at: " + workingdir);
					executionWorkingDirectoryFileLock.lock();
				}
				filetounlock = executionWorkingDirectoryFileLock;
			} catch (IOException e) {
				SakerLog.warning().verbose().println("Failed to create lock file at: " + workingdir + " (" + e + ")");
			}
			checkExecutionSetupCancelled(monitor, currentthread);

			ExecutionRepositoryConfiguration repoconfig = parameters.getRepositoryConfiguration();
			ExecutionScriptConfiguration scriptconfiguration = parameters.getScriptConfiguration();
			Map<String, String> userparams = ImmutableUtils.makeImmutableNavigableMap(parameters.getUserParameters());

			//wait the finalizer thread without interruption so the database is flushed properly
			//   so the next time it is read with newly loaded repositories, the data won't be corrupted
			waitExecutionFinalizerThread(false);

			checkExecutionSetupCancelled(monitor, currentthread);

			//TODO handle the exception from the following .set call
			boolean executioncachechanged = executionCache.set(pathconfig, repoconfig, scriptconfiguration, userparams,
					LocalFileProvider.getProviderKeyStatic());

			if (executioncachechanged) {
				clearExecutionCacheChangeRelatedResources();
			}

			checkExecutionSetupCancelled(monitor, currentthread);

			DatabaseConfiguration dbconfig = parameters.getDatabaseConfiguration();
			ProviderHolderPathKey dbfilepath = builddirectory == null ? null
					: pathconfig.getPathKey(builddirectory.resolve(ContentDatabaseImpl.FILENAME_DATABASE));
			if (executionDatabase == null) {
				if (dbfilepath == null) {
					this.executionDatabase = new ContentDatabaseImpl(dbconfig, pathconfig);
				} else {
					this.executionDatabase = new ContentDatabaseImpl(dbconfig, pathconfig,
							executionCache.getExecutionClassLoaderResolver(), dbfilepath);
				}
				this.executionDatabase.setTrackHandleAttributes(true);
			} else {
				if (!executionDatabase.isConfiguredTo(pathconfig, dbfilepath, dbconfig)
						|| !executionDatabase.isDatabaseFileExists()) {
					//close the current database, and reopen
					IOUtils.closePrint(this.executionDatabase);
					this.executionDatabase = new ContentDatabaseImpl(dbconfig, pathconfig,
							executionCache.getExecutionClassLoaderResolver(), dbfilepath);
					this.executionDatabase.setTrackHandleAttributes(true);
					//clear the cached repositories, as they are bound to the old content database
				}
			}

			checkExecutionSetupCancelled(monitor, currentthread);

			if (executionFileWatchHandler != null) {
				ContentDatabaseImpl watcheddb = executionFileWatchHandler.getCurrentDatabase();
				if (watcheddb != null) {
					if (watcheddb == this.executionDatabase) {
						executionFileWatchHandler.stopRecheckPathsOpt();
					} else {
						//we need to clear the cached directories, as they are bound to different database
						executionFileWatchHandler.stopClearCachedDirectories();
					}
				}
			}

			execlocktounlock = null;
			filetounlock = null;
		} catch (InterruptedException e) {
			throw new ProjectExecutionSetupCancelledException(e);
		} finally {
			if (execlocktounlock != null) {
				execlocktounlock.release();
			}
			if (filetounlock != null) {
				try {
					filetounlock.release();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void executionFinished(ExecutionContextImpl context, ExecutionPathConfiguration pathconfig,
			Object environmentexecutionkey, SakerPath builddirectory, FileContentDataComputeHandler filecomputehandler,
			Map<TaskIdentifier, TaskExecutionResult<?>> cachedktaskstopublish, BuildCacheAccessor buildcacheaccessor,
			InternalBuildTrace buildtrace) {
		try {
			if (this.executionFinalizerThread != null) {
				throw new IllegalStateException("Execution was not started.");
			}
			startingRequested = false;
			if (this.executionDatabase != null) {
				if (!this.executionDatabase.isPersisting()) {
					//just release the database, as it is not saved to any path anyway
					this.executionDatabase = null;
				}
			}
			this.cachedExecutionFileContentDataComputeHandler = filecomputehandler;
			if (this.executionDatabase != null || fileWatchingEnabled) {
				ThreadGroup tg = new ThreadGroup(this.environment.getEnvironmentThreadGroup(), "Execution finalizer");
				tg.setDaemon(true);

				Map<String, ? extends SakerDirectory> rootdirs = ObjectUtils.newTreeMap(context.getRootDirectories());

				executionFinalizerThread = new Thread(tg, () -> {
					try {
						if (filecomputehandler != null) {
							filecomputehandler.cacheify();
						}
						runExecutionFinisherThreadImpl(pathconfig, rootdirs, builddirectory, cachedktaskstopublish,
								buildcacheaccessor, buildtrace);
					} catch (Throwable e) {
						e.printStackTrace();
						throw e;
					} finally {
						this.environment.executionFinished(environmentexecutionkey);
					}
				}, "Execution finalizer");
				//the finalizer thread is not daemon, as we need to flush the database before exiting the VM
				executionFinalizerThread.start();
			} else {
				try {
					if (filecomputehandler != null) {
						filecomputehandler.cacheify();
					}
				} finally {
					this.environment.executionFinished(environmentexecutionkey);
				}
			}
		} finally {
			IOUtils.closePrint(executionWorkingDirectoryFileLock);
			executionWorkingDirectoryFileLock = null;
			usageLock.release();
		}
	}

	private void clearExecutionCacheChangeRelatedResources() {
		IOUtils.closePrint(this.executionDatabase);
		this.executionDatabase = null;
		this.cachedExecutionFileContentDataComputeHandler = null;

		IOUtils.closePrint(executionFileWatchHandler);
		executionFileWatchHandler = null;
		IOUtils.closePrint(clusterFileWatchHandler);
		clusterFileWatchHandler = null;

		IOUtils.closePrint(this.clusterDatabase);
		this.clusterDatabase = null;

		clusterMirrorHandler = null;
	}

	private void lockUsageLockForExecution(ExecutionProgressMonitor monitor, SakerPath workingdir,
			Thread currentthread) {
		boolean locked = usageLock.tryAcquire();
		if (!locked) {
			SakerLog.log().verbose().println("Awaiting execution lock for project at: " + workingdir);
			while (!locked) {
				try {
					locked = usageLock.tryAcquire(1, TimeUnit.SECONDS);
					if (locked) {
						break;
					}
					if (monitor.isCancelled()) {
						throw new ProjectExecutionSetupCancelledException("cancelled");
					}
				} catch (InterruptedException e) {
					currentthread.interrupt();
					throw new ProjectExecutionSetupCancelledException(e);
				}
			}
		}
	}

	private static void checkExecutionSetupCancelled(ExecutionProgressMonitor monitor, Thread currentthread) {
		if (monitor.isCancelled()) {
			throw new ProjectExecutionSetupCancelledException("cancelled");
		} else if (currentthread.isInterrupted()) {
			throw new ProjectExecutionSetupCancelledException("interrupted");
		}
	}

	private void waitExecutionFinalizerThread(boolean interrupt) throws InterruptedException {
		Thread t = executionFinalizerThread;
		if (t != null) {
			if (interrupt) {
				t.interrupt();
			}
			t.join();
			executionFinalizerThread = null;
		}
	}

	private void waitExecutionFinalizerThreadNonInterruptible(boolean interrupt) {
		boolean interrupted = false;
		while (true) {
			try {
				waitExecutionFinalizerThread(interrupt);
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private static void executeDatabaseFlush(ContentDatabaseImpl database) {
		try {
			database.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void runClusterFinisherThreadImpl() {
		if (this.clusterDatabase != null) {
			if (fileWatchingEnabled) {
				if (clusterFileWatchHandler == null) {
					clusterFileWatchHandler = new ProjectFileChangesWatchHandler();
				}
				clusterFileWatchHandler.watch(clusterDatabase, null, null);
			}
			executeDatabaseFlush(clusterDatabase);
			if (!this.fileWatchingEnabled) {
				clusterDatabase.invalidateAllOffline();
			}
		}
	}

	private void runExecutionFinisherThreadImpl(ExecutionPathConfiguration pathconfig,
			Map<String, ? extends SakerDirectory> rootdirs, SakerPath builddirectory,
			Map<TaskIdentifier, TaskExecutionResult<?>> cachedktaskstopublish, BuildCacheAccessor buildcacheaccessor,
			InternalBuildTrace buildtrace) {
		try {
			if (this.executionDatabase != null) {
				this.executionDatabase.handleAbandonedTasksOffline(pathconfig, builddirectory);
				if (this.fileWatchingEnabled) {
					if (executionFileWatchHandler == null) {
						executionFileWatchHandler = new ProjectFileChangesWatchHandler();
					}
					executionFileWatchHandler.watch(executionDatabase, rootdirs,
							cachedExecutionFileContentDataComputeHandler);
				}
				executeDatabaseFlush(this.executionDatabase);
				if (!cachedktaskstopublish.isEmpty()) {
					try {
						buildcacheaccessor.publishCachedTasks(cachedktaskstopublish, this.executionDatabase,
								pathconfig);
					} catch (IOException e) {
						// TODO handle cache publishing exception
						e.printStackTrace();
					}
				}
				IOUtils.closePrint(buildtrace);
				if (!this.fileWatchingEnabled) {
					//invalidate the whole database, as we don't watch the disk contents for changes
					//    therefore they need to be rechecked when needed without caching
					this.executionDatabase.invalidateAllOffline();
				}
			}
		} finally {
			MemoryTrimmer.trimInterruptible(() -> this.startingRequested || this.closed);
		}
	}

}
