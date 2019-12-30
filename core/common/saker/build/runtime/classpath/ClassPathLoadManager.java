package saker.build.runtime.classpath;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.CloseProtectedClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.JarClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.classloader.SakerPathClassLoaderDataFinder;
import testing.saker.build.flag.TestFlag;

/**
 * Manager class for loading classpaths.
 * <p>
 * This class can be used to load classpaths for given {@linkplain ClassPathLocation locations}. The manager allows the
 * classpaths to be used by multiple processes, by keeping a lock on specific files in the file system.
 * <p>
 * The manager is instantiated with a given storage directory where the classpath files will be loaded in an
 * implementation dependent hierarchy.
 * <p>
 * As the manager allows concurrent access to classpaths from multiple processes, a scenario can emerge where one
 * manager uses the classpath, but one tries to reload it. In this case the second manager won't be able to reload the
 * classpath, and will use the one that is currently used by the first process. In order to ensure that a classpath is
 * reloaded when required, the user should manually clear loaded classpaths and restart the processes necessary. This
 * issue only happens when multiple load managers are configured to the <i>same</i> storage directory.
 * <p>
 * The manager spawns an owned thread which is responsible of garbage collection unreleased classpaths by the user. This
 * doesn't replace the need to call {@link ClassPathLock#close()}, but only a safety feature.
 */
public class ClassPathLoadManager implements Closeable {
	private static final String STORAGE_SUBDIRECTORY_NAME = "classpath";

	/**
	 * A handle and a lock to a loaded classpath.
	 * <p>
	 * Instances of this interface must be {@linkplain ClassPathLock#close() closed} when no longer used. Closing it
	 * will allow classpath managers to reload the underlying classpath files.
	 */
	public interface ClassPathLock extends Closeable {
		/**
		 * Gets a classloader data finder for the loaded classpath.
		 * <p>
		 * Classloader data finders can be used to construct a classloader. See {@link MultiDataClassLoader}.
		 * 
		 * @return The classloader data finder.
		 * @see MultiDataClassLoader
		 */
		public ClassLoaderDataFinder getClassLoaderDataFinder();

		/**
		 * Gets the path to the loaded classpath.
		 * <p>
		 * The returned path is either a path to a JAR file, or a directory containing class files in the respective
		 * package hierarchy.
		 * 
		 * @return The path to the classpath.
		 */
		public Path getClassPathPath();

		/**
		 * Gets the directory where the classpath was actually loaded.
		 * 
		 * @return The path to the load directory.
		 * @see ClassPathLoader#loadTo(ProviderHolderPathKey)
		 */
		public Path getClassPathLoadDirectory();

		/**
		 * Gets the location identifier which was returned by the corresponding {@link ClassPathLocation}.
		 * 
		 * @return The location identifier.
		 * @see ClassPathLocation#getIdentifier()
		 */
		public String getLocationIdentifier();

		/**
		 * Gets a version object representation of the loaded classpath.
		 * <p>
		 * Version objects can be compared by {@linkplain Object#equals(Object) equality} to check if the classpath was
		 * reloaded between different lock retrievals. Clients can be sure that if two version objects
		 * {@linkplain Object#equals(Object) equal}, then the associated classpaths to the version objects contain the
		 * same files.
		 * <p>
		 * Note, that even if the compared versions equal, the associated {@linkplain #getClassLoaderDataFinder() class
		 * loader data finder} may have been reloaded meanwhile. This means that if you plan on reusing the class
		 * loaders based on the associated class loader data finder, then you need to ensure that always the most recent
		 * class loader data finder is used by your classloader.
		 * 
		 * @return The load version.
		 * @see #getClassLoaderDataFinder()
		 */
		public Object getVersion();

		@Override
		public void close();
	}

	private static final Map<Path, LoaderLockFileHandle> PATH_LOCKFILE_HANDLES = new ConcurrentSkipListMap<>();

	private static final Random CLASSPATH_LOADING_RANDOMER = new SecureRandom();
	private static final int READLOCK_REGION_LENGTH = 4;
	private static final int LOCKFILE_STATE_DATA_LENGTH = 1024 * 4;

	private final Path storageDirectoryPath;
	/**
	 * Maps classpath load directories to their load states.
	 */
	private final ConcurrentNavigableMap<Path, ClassPathLoadState> loadDirectoryLoadStates = new ConcurrentSkipListMap<>();
	private final ConcurrentNavigableMap<Path, Object> loadDirectoryLoadStatesLocks = new ConcurrentSkipListMap<>();

	private volatile boolean closed = false;

	private ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
	private Thread cleanerThread;

	/**
	 * Creates a new classpath manager with the current {@link ThreadGroup} as a base.
	 * 
	 * @param storagedirectory
	 *            The storage directory for the manager to use.
	 * @see ClassPathLoadManager#ClassPathLoadManager(ThreadGroup, Path)
	 */
	public ClassPathLoadManager(Path storagedirectory) {
		this(null, storagedirectory);
	}

	/**
	 * Creates a new classpath manager for the given thread group and storage directory.
	 * 
	 * @param threadgroup
	 *            The thread group to use when creating the garbage collecting thread, or <code>null</code> to use the
	 *            current one.
	 * @param storagedirectory
	 *            The storage directory for the manager to use.
	 */
	public ClassPathLoadManager(ThreadGroup threadgroup, Path storagedirectory) {
		SakerPathFiles.requireAbsolutePath(storagedirectory);

		this.storageDirectoryPath = storagedirectory.normalize();

		cleanerThread = new Thread(threadgroup, new CleanerRunnable(new WeakReference<>(this, refQueue), refQueue),
				getClass().getSimpleName() + "-cleaner");
		cleanerThread.setContextClassLoader(null);
		cleanerThread.setDaemon(true);
		cleanerThread.start();
	}

	/**
	 * Loads the classpath for the given location and places a lock on it.
	 * <p>
	 * The manager will try to load the classpath if it is not locked by other processes. <br>
	 * If it is locked, it will return a handle to the already loaded classpath, which is shared with other processes.
	 * This may be a previous version of the expected classes.
	 * <p>
	 * In order to force reloading the classes, the client should externally make sure that no processes have a lock on
	 * the specified classpath.
	 * 
	 * @param classpathlocation
	 *            The classpath location to load.
	 * @return A handle to the loaded classpath.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ClassPathLock loadClassPath(ClassPathLocation classpathlocation) throws IOException {
		String locationid = classpathlocation.getIdentifier();
		Path loaddir = getClassPathLoadDirectory(locationid);
		synchronized (loadDirectoryLoadStatesLocks.computeIfAbsent(loaddir, Functionals.objectComputer())) {
			checkClosed();
			ClassPathLoadState loadstate = loadDirectoryLoadStates.get(loaddir);
			if (loadstate == null) {
				//create
				loadstate = createLoadState(loaddir);
				loadDirectoryLoadStates.put(loaddir, loadstate);
			}
			return loadstate.createLock(classpathlocation);
		}
	}

	/**
	 * Loads a classpath at the given load directory, given it was already loaded successfully by an other agent.
	 * <p>
	 * Only use this method if you are sure that the classpath at the specified directory has been successfully loaded
	 * by an other agent.
	 * <p>
	 * Example use case: <br>
	 * Some classes were loaded from classpath C. The implementation decides that it wants to spawn a new process P.
	 * When P is started, it receives the classpath load directory D as an argument, and tries to load the classpath
	 * directly using this method. It will be successful, as C already loaded the classpath successfully to the
	 * directory D, therefore calling this method in process P with the argument directory D will likely succeed.
	 * <p>
	 * As a result of the above example, multiple processes will hold a lock on the classpath C.
	 * 
	 * @param classpathloaddirectory
	 *            The path to the loaded classpath.
	 * @return A handle to the loaded classpath.
	 * @throws IOException
	 *             In case of I/O error, or the classpath is not successfully loaded at the location.
	 * @see {@link ClassPathLock#getClassPathLoadDirectory()}
	 */
	public ClassPathLock loadDirectClassPath(Path classpathloaddirectory) throws IOException {
		SakerPathFiles.requireAbsolutePath(classpathloaddirectory);
		classpathloaddirectory = classpathloaddirectory.normalize();

		synchronized (loadDirectoryLoadStatesLocks.computeIfAbsent(classpathloaddirectory,
				Functionals.objectComputer())) {
			checkClosed();
			ClassPathLoadState loadstate = loadDirectoryLoadStates.get(classpathloaddirectory);
			if (loadstate == null) {
				//create
				loadstate = createLoadState(classpathloaddirectory);
				loadDirectoryLoadStates.put(classpathloaddirectory, loadstate);
			}
			return loadstate.createLock(null);
		}
	}

	/**
	 * Gets the storage directory which was used to create this classpath manager.
	 * 
	 * @return The storage directory.
	 */
	public Path getStorageDirectoryPath() {
		return storageDirectoryPath;
	}

	/**
	 * Gets the classpath load directory for a given classpath location.
	 * <p>
	 * This method doesn't ensure that the directory actually exists at the returned path.
	 * 
	 * @param location
	 *            The classpath location.
	 * @return The load directory.
	 * @see {@link ClassPathLoader#loadTo(ProviderHolderPathKey)}
	 * @see ClassPathLock#getClassPathLoadDirectory()
	 */
	public Path getClassPathLoadDirectoryPath(ClassPathLocation location) {
		return getClassPathLoadDirectoryPath(location.getIdentifier());
	}

	@Override
	public void close() throws IOException {
		closed = true;
		refQueue = null;
		IOException exc = null;
		for (Entry<Path, Object> entry : loadDirectoryLoadStatesLocks.entrySet()) {
			synchronized (entry.getValue()) {
				exc = IOUtils.closeExc(loadDirectoryLoadStates.remove(entry.getKey()));
			}
		}
		cleanerThread.interrupt();
		try {
			cleanerThread.join();
		} catch (InterruptedException e) {
			//reinterrupt thread
			Thread.currentThread().interrupt();
			exc = IOUtils.addExc(exc, e);
		}
		//clear just in case
		exc = IOUtils.closeExc(loadDirectoryLoadStates.values());
		loadDirectoryLoadStates.clear();
		IOUtils.throwExc(exc);
	}

	private static final class CleanerRunnable implements Runnable {
		private final WeakReference<ClassPathLoadManager> thisref;
		private final ReferenceQueue<Object> refq;

		private CleanerRunnable(WeakReference<ClassPathLoadManager> thisref, ReferenceQueue<Object> refq) {
			this.thisref = thisref;
			this.refq = refq;
		}

		@Override
		public void run() {
			try {
				while (true) {
					//interruption is checked in remove() waiting call
					Reference<? extends Object> ref = refq.remove();
					if (ref == thisref) {
						break;
					}
					ClassPathLoadManager loadmanager = thisref.get();
					if (loadmanager == null) {
						//the manager has been garbage collected, exit.
						break;
					}
					if (ref instanceof ReturnedLockReference) {
						((ReturnedLockReference) ref).loadState.handlePossibleLockAbandonment();
					} else if (ref instanceof ReturnedFinderReference) {
						((ReturnedFinderReference) ref).loadState.handlePossibleLockAbandonment();
					}
				}
			} catch (InterruptedException e) {
			}
		}
	}

	private Path getStorageDirectoryPath(String identifier) {
		Path storagedir = storageDirectoryPath;
		Path result = storagedir.resolve(identifier);
		return result;
	}

	private Path getStorageDirectory(String identifier) throws IOException {
		Path storagedir = storageDirectoryPath;
		Path result = storagedir.resolve(identifier);
		LocalFileProvider.getInstance().createDirectories(result);
		return result;
	}

	private void checkClosed() {
		if (closed) {
			throw new IllegalStateException("closed");
		}
	}

	private ClassPathLoadState createLoadState(Path loaddir) {
		return new ClassPathLoadState(loaddir);
	}

	private Path getClassPathLoadDirectoryPath(String locationid) {
		return getStorageDirectoryPath(STORAGE_SUBDIRECTORY_NAME + "/load/" + locationid);
	}

	private Path getClassPathLoadDirectory(String locationid) throws IOException {
		return getStorageDirectory(STORAGE_SUBDIRECTORY_NAME + "/load/" + locationid);
	}

	private static long randomReadLockOffset() {
		while (true) {
			long offset = CLASSPATH_LOADING_RANDOMER.nextLong();
			if (offset >= LOCKFILE_STATE_DATA_LENGTH && offset <= Long.MAX_VALUE - READLOCK_REGION_LENGTH) {
				return offset;
			}
		}
	}

	private static FileLock tryLockFileNullOverlappingException(FileChannel lockchannel, long position, long size,
			boolean shared) throws IOException {
		try {
			return lockchannel.tryLock(position, size, shared);
		} catch (OverlappingFileLockException e) {
		}
		return null;
	}

	private static class ReturnedFinderReference extends WeakReference<ClassLoaderDataFinder> {
		protected ClassPathLoadState loadState;

		public ReturnedFinderReference(ClassLoaderDataFinder referent, ReferenceQueue<? super ClassLoaderDataFinder> q,
				ClassPathLoadState loadState) {
			super(referent, q);
			this.loadState = loadState;
		}

	}

	private static class ReturnedLockReference extends WeakReference<ClassPathLoadState.StateLoadLockImpl> {
		protected ClassPathLoadState loadState;

		public ReturnedLockReference(ClassPathLoadState.StateLoadLockImpl referent,
				ReferenceQueue<? super ClassPathLoadState.StateLoadLockImpl> q) {
			super(referent, q);
			this.loadState = referent.getEnclosingLoadState();
		}

	}

	private class ClassPathLoadState implements Closeable {
		protected final Path classPathLoadDirectory;
		private LoaderLockFileHandle lockFileHandle;

		private Set<StateLoadLockImpl> locks = ObjectUtils.newSetFromMap(new WeakHashMap<>());
		private Set<ClassLoaderDataFinder> returnedDataFinders = ObjectUtils.newSetFromMap(new WeakHashMap<>());

		protected Path classPathPath;
		protected UUID currentLoadUUID;
		protected String locationIdentifier;
		protected ClassLoaderDataFinder loadedDataFinder;

		protected ClassPathLoader loader;

		protected ConcurrentPrependAccumulator<ReturnedFinderReference> returnedClassLoaderDataFinderReferences = new ConcurrentPrependAccumulator<>();
		protected ConcurrentPrependAccumulator<ReturnedLockReference> returnedLockReferences = new ConcurrentPrependAccumulator<>();

		private FileLock readLock;

		public ClassPathLoadState(Path classpathloaddirectory) {
			this.classPathLoadDirectory = classpathloaddirectory;
			Path loadlockfilepath = classpathloaddirectory
					.resolveSibling("." + classpathloaddirectory.getFileName() + ".loadlock");
			this.lockFileHandle = PATH_LOCKFILE_HANDLES.computeIfAbsent(loadlockfilepath, LoaderLockFileHandle::new);
			this.lockFileHandle.addUsercount();
		}

		public Path getClassPathLoadDirectory() {
			return classPathLoadDirectory;
		}

		public ClassPathLock createLock(ClassPathLocation classpathlocation) throws IOException {
			synchronized (this) {
				StateLoadLockImpl result = new StateLoadLockImpl();
				ensureClassPathLoadedLocked(classpathlocation, result);
				locks.add(result);
				return returnLock(result);
			}
		}

		private ClassPathLock returnLock(StateLoadLockImpl result) {
			returnedLockReferences.add(new ReturnedLockReference(result, refQueue));
			return result;
		}

		protected void returningDataFinder(CloseProtectedClassLoaderDataFinder finder) {
			ReturnedFinderReference ref = new ReturnedFinderReference(finder, refQueue, this);
			synchronized (this) {
				returnedDataFinders.add(finder);
			}
			returnedClassLoaderDataFinderReferences.add(ref);
		}

		public Path getClassPathPath() {
			return classPathPath;
		}

		@Override
		public void close() throws IOException {
			synchronized (this) {
				if (lockFileHandle == null) {
					return;
				}
				clearLoadState();
				IOException exc = null;
				exc = IOUtils.closeExc(exc, locks);
				locks.clear();
				exc = IOUtils.closeExc(exc, readLock);
				readLock = null;
				lockFileHandle.releaseUserCount();
				lockFileHandle = null;
				IOUtils.throwExc(exc);
			}
		}

		@Override
		public String toString() {
			return "ClassPathLoadState["
					+ (classPathLoadDirectory != null ? "classPathLoadDirectory=" + classPathLoadDirectory : "") + "]";
		}

		protected void unlock(StateLoadLockImpl lock) {
			synchronized (this) {
				if (!locks.remove(lock)) {
					return;
				}
				if (locks.isEmpty()) {
					clearLoadState();
					IOUtils.closePrint(readLock);
					readLock = null;
				}
			}
		}

		protected void handlePossibleLockAbandonment() {
			synchronized (this) {
				if (!locks.isEmpty() || !returnedDataFinders.isEmpty()) {
					//there are still locks or data finders referenced by the client
					return;
				}
				clearLoadState();
				IOUtils.closePrint(readLock);
				readLock = null;
			}
		}

//		protected boolean tryReloadIfChanged(StateLoadLockImpl lock, Runnable run) throws IOException {
//			synchronized (this) {
//				if (this.location == null) {
//					return false;
//				}
//				if (locks.size() != 1) {
//					return false;
//				}
//				if (!locks.contains(lock)) {
//					return false;
//				}
//				//the lock is the only lock present
//				//unlock temporarily and then try to reload
//				locks.clear();
//				IOUtils.closePrint(readLock);
//				readLock = null;
//				ClassLoaderDataFinder datafinder = this.loadedDataFinder;
//				ensureClassPathLoadedLocked(null, lock, run);
//				//if the data finder changed, then the classpath was reloaded
//				return datafinder != this.loadedDataFinder;
//			}
//		}

		private class StateLoadLockImpl implements ClassPathLock {
			private boolean closed = false;

			@Override
			public synchronized ClassLoaderDataFinder getClassLoaderDataFinder() {
				checkClosed();
				CloseProtectedClassLoaderDataFinder result = new CloseProtectedClassLoaderDataFinder(
						ClassPathLoadState.this.loadedDataFinder);
				returningDataFinder(result);
				return result;
			}

			@Override
			public synchronized Path getClassPathPath() {
				checkClosed();
				return ClassPathLoadState.this.getClassPathPath();
			}

			@Override
			public synchronized Path getClassPathLoadDirectory() {
				checkClosed();
				return ClassPathLoadState.this.getClassPathLoadDirectory();
			}

			@Override
			public synchronized String getLocationIdentifier() {
				checkClosed();
				return ClassPathLoadState.this.locationIdentifier;
			}

			@Override
			public Object getVersion() {
				return currentLoadUUID;
			}

//			@Override
//			public synchronized boolean tryReloadIfchanged(Runnable run) throws IOException {
//				checkClosed();
//				return ClassPathLoadState.this.tryReloadIfChanged(this, run);
//			}

			@Override
			public synchronized void close() {
				if (closed) {
					return;
				}
				closed = true;
				unlock(this);
			}

			public ClassPathLoadState getEnclosingLoadState() {
				return ClassPathLoadState.this;
			}

			private void checkClosed() {
				if (closed) {
					throw new IllegalStateException("closed");
				}
			}

		}

		private boolean tryAcquireReadLock(FileChannel lockchannel, StateLoadLockImpl loadlock) throws IOException {
			if (readLock != null) {
				throw new AssertionError("readlock is not null.");
			}
			for (int i = 0; i < 3; i++) {
				long offset = randomReadLockOffset();
				readLock = tryLockFileNullOverlappingException(lockchannel, offset, READLOCK_REGION_LENGTH, false);
				if (readLock != null) {
					//successfully acquired read lock with loaded classpath
					this.locks.add(loadlock);
					return true;
				}
			}
			return false;
		}

		//suppress the unused varning of the state lock
		@SuppressWarnings("try")
		private void ensureClassPathLoaded(StateLoadLockImpl loadlock, ClassPathLocation thislocation)
				throws IOException {
			if (!locks.isEmpty() || readLock != null) {
				throw new AssertionError(locks + " --- " + readLock);
			}
			LocalFileProvider localfiles = LocalFileProvider.getInstance();
			ProviderHolderPathKey targetdirpathkey = localfiles.getPathKey(classPathLoadDirectory);
			SakerPath dirpath = targetdirpathkey.getPath();

			//TODO this synchronization should be timed locking
			try {
				lockFileHandle.acquireLock();
			} catch (InterruptedException e) {
				//set back the flag
				Thread.currentThread().interrupt();
				throw new InterruptedIOException("Classpath loading lock acquire was interrupted.");
			}
			try {
				RandomAccessFile raf = lockFileHandle.getFileLocked();
				FileChannel lockchannel = raf.getChannel();
				while (true) {
					try (FileLock statelock = lockchannel.lock(0, LOCKFILE_STATE_DATA_LENGTH, false)) {
						raf.seek(0);
						boolean wasloaded;
						try {
							wasloaded = raf.readBoolean();
						} catch (EOFException e) {
							wasloaded = false;
						}
						if (!wasloaded) {
							//the loading failed by some other agent
							//currently there must be no users of the classpath,
							//    because using a failed load state is invalid
							//lock the remaining of the lock file and load the classpath
							if (thislocation == null) {
								throw new IOException(
										"No classpath location specified to load at: " + classPathLoadDirectory);
							}

							final String thislocationidentifier = thislocation.getIdentifier();
							try (FileLock writelock = lockchannel.tryLock(LOCKFILE_STATE_DATA_LENGTH,
									Long.MAX_VALUE - LOCKFILE_STATE_DATA_LENGTH, false)) {
								if (writelock == null) {
									throw new AssertionError("Failed to lock remaining part of lock file.");
								}

								executeClassPathLoadingAndWriteFileState(thislocation, localfiles, targetdirpathkey,
										dirpath, thislocationidentifier, raf);
							}
						} else {
							//the classpath is in a successfully loaded state
							//the classpath loading was successful by some other agent
							//it is still is use by some other agent, 
							//    we can't load the classpath again, as that would corrupt the other agent
							//we go ahead and load the classpath without loading
							//    this is acceptable as reloading a classpath requires all users to release it first
							SakerPath readrelative = SakerPath.valueOf(raf.readUTF());
							String filelocationidentifier = raf.readUTF();

							UUID loaduuid = UUID.fromString(raf.readUTF());
							try (FileLock writelock = tryLockFileNullOverlappingException(lockchannel,
									LOCKFILE_STATE_DATA_LENGTH, Long.MAX_VALUE - LOCKFILE_STATE_DATA_LENGTH, false)) {
								if (writelock == null) {
									//some other agents are still using the classpath, do not try to load now
									if (!loaduuid.equals(currentLoadUUID)) {
										fillClassPathLoadData(localfiles, dirpath, readrelative, loaduuid,
												filelocationidentifier);
									}
									//else the currently loaded classpath is already the same
								} else {
									//the write lock was acquired
									//no other agents are using this classpath
									if (loader != null && !loader.isChanged() && loaduuid.equals(currentLoadUUID)) {
										//nothing have changed.
										//   exit the try block and try acquiring the read lock
									} else {
										//the classpath changed or not yet loaded, try to load
										if (thislocation == null) {
											fillClassPathLoadData(localfiles, dirpath, readrelative, loaduuid,
													filelocationidentifier);
										} else {
											executeClassPathLoadingAndWriteFileState(thislocation, localfiles,
													targetdirpathkey, dirpath, thislocation.getIdentifier(), raf);
										}
									}
								}
							}
						}
						if (tryAcquireReadLock(lockchannel, loadlock)) {
							return;
						}
						continue;
					}
				}
			} finally {
				lockFileHandle.releaseLock();
			}
			//unreachable
		}

		private void executeClassPathLoadingAndWriteFileState(ClassPathLocation location, LocalFileProvider localfiles,
				ProviderHolderPathKey targetdirpathkey, SakerPath dirpath, String locationidentifier,
				RandomAccessFile raf) throws IOException {
			try {
				SakerPath relative = executeClassPathLoading(location, localfiles, targetdirpathkey, dirpath,
						locationidentifier);
				raf.seek(0);
				raf.writeBoolean(true);
				raf.writeUTF(relative.toString());
				raf.writeUTF(locationidentifier);
				raf.writeUTF(currentLoadUUID.toString());
			} catch (IOException e) {
				//failed to load the classpath, throw the exception
				try {
					raf.seek(0);
					raf.writeBoolean(false);
				} catch (IOException e2) {
					e.addSuppressed(e2);
				}
				throw e;
			}
		}

		private void clearLoadState() {
			if (TestFlag.ENABLED) {
				if (loadedDataFinder != null) {
					TestFlag.metric().classPathUnloadedAtPath(classPathLoadDirectory);
				}
			}
			IOUtils.closePrint(loadedDataFinder);
			this.loadedDataFinder = null;
			this.classPathPath = null;
			this.currentLoadUUID = null;
			this.locationIdentifier = null;
			this.returnedClassLoaderDataFinderReferences.clear();
			this.returnedLockReferences.clear();
			this.returnedDataFinders.clear();
		}

		private SakerPath executeClassPathLoading(ClassPathLocation location, LocalFileProvider localfiles,
				ProviderHolderPathKey targetdirpathkey, SakerPath dirpath, String locationid) throws IOException {
			this.loader = location.getLoader();
			clearLoadState();
			SakerPath relative = loader.loadTo(targetdirpathkey);
			UUID newuuid = new UUID(CLASSPATH_LOADING_RANDOMER.nextLong(), CLASSPATH_LOADING_RANDOMER.nextLong());

			fillClassPathLoadData(localfiles, dirpath, relative, newuuid, locationid);
			return relative;
		}

		private void fillClassPathLoadData(LocalFileProvider localfiles, SakerPath dirpath, SakerPath relative,
				UUID newuuid, String locationid) throws IOException {
			SakerPath classpathabsolute = dirpath.resolve(relative);
			FileEntry cpattrs = localfiles.getFileAttributes(classpathabsolute);

			ClassLoaderDataFinder datafinder;
			if (cpattrs.isDirectory()) {
				datafinder = new SakerPathClassLoaderDataFinder(localfiles, classpathabsolute);
			} else if (FileUtils.hasExtensionIgnoreCase(classpathabsolute.getFileName(), "jar")) {
				datafinder = new JarClassLoaderDataFinder(LocalFileProvider.toRealPath(classpathabsolute));
			} else {
				throw new IOException("Unsupported class path: " + classpathabsolute);
			}
			clearLoadState();
			this.classPathPath = LocalFileProvider.toRealPath(classpathabsolute);
			this.loadedDataFinder = datafinder;
			this.currentLoadUUID = newuuid;
			this.locationIdentifier = locationid;

			if (TestFlag.ENABLED) {
				TestFlag.metric().classPathLoadedAtPath(classPathLoadDirectory);
			}
		}

		private void ensureClassPathLoadedLocked(ClassPathLocation location, StateLoadLockImpl loadlock)
				throws IllegalArgumentException, IOException {
			if (locks.isEmpty()) {
				ensureClassPathLoaded(loadlock, location);
			}
		}

	}

	private static class LoaderLockFileHandle {
		private final Path path;
		private final Semaphore lock = new Semaphore(1);
		private RandomAccessFile lockFile;
		private int userCount = 0;

		public LoaderLockFileHandle(Path path) {
			this.path = path;
		}

		public void acquireLock() throws InterruptedException {
			lock.acquire();
		}

		public boolean tryAcquireLock(long timeout, TimeUnit unit) throws InterruptedException {
			return lock.tryAcquire(timeout, unit);
		}

		public void releaseLock() {
			lock.release();
		}

		public RandomAccessFile getFileLocked() throws FileNotFoundException {
			if (lockFile != null) {
				return lockFile;
			}
			lockFile = new RandomAccessFile(path.toFile(), "rw");
			return lockFile;
		}

		public void releaseUserCount() {
			synchronized (this) {
				--userCount;
				if (userCount == 0) {
					boolean acquired = lock.tryAcquire();
					if (!acquired) {
						throw new AssertionError("No lock file users are present, and failed to acquire handle lock.");
					}
					try {
						if (lockFile != null) {
							IOUtils.closePrint(lockFile);
							lockFile = null;
						}
					} finally {
						lock.release();
					}
				}
			}
		}

		public void addUsercount() {
			synchronized (this) {
				++userCount;
			}
		}
	}
}
