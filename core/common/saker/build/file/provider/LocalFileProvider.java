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
package saker.build.file.provider;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Supplier;

import saker.apiextract.api.ExcludeApi;
import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimplePathKey;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.LocalFileProviderImpl.RMIBufferedFileInputByteSource;
import saker.build.file.provider.LocalFileProviderImpl.RMIBufferedFileOutputByteSink;
import saker.build.file.provider.LocalFileProviderInternalRMIAccess.MultiByteArray;
import saker.build.file.provider.LocalFileProviderInternalRMIAccess.OpenedRMIBufferedFileInput;
import saker.build.file.provider.LocalFileProviderInternalRMIAccess.RMIBufferedFileInput;
import saker.build.file.provider.LocalFileProviderInternalRMIAccess.RMIBufferedFileOutput;
import saker.build.file.provider.LocalFileProviderInternalRMIAccess.RMIBufferedReadResult;
import saker.build.file.provider.LocalFileProviderInternalRMIAccess.RMIWriteToResult;
import saker.build.meta.PropertyNames;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.InputStreamByteSource;
import saker.build.thirdparty.saker.util.io.OutputStreamByteSink;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.ref.WeakReferencedToken;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.trace.InternalBuildTraceImpl;
import saker.build.util.config.ReferencePolicy;
import saker.osnative.watcher.NativeWatcherService;
import saker.osnative.watcher.RegisteringWatchService;
import saker.osnative.watcher.WatchRegisterer;
import testing.saker.build.flag.TestFlag;

/**
 * Root file provider backed by the local file system.
 * <p>
 * The local file provider operates on {@link Path Paths} and uses the local {@link FileSystem} instance for modifying
 * the files. The instance is retrieved using {@link FileSystems#getDefault()}.
 * <p>
 * Some methods in this class can accept {@link Path} instead of {@link SakerPath}. The passed paths should all be bound
 * to the default filesystem, if they're not, an {@link InvalidPathFormatException} is thrown.
 * <p>
 * Singleton, use {@link #getInstance()} to retrieve an instance.
 */
@PublicApi
public abstract class LocalFileProvider implements SakerFileProvider {
	private static final String UUID_FILE_NAME = "saker.files.uuid";
	private static final Set<FileVisitOption> FOLLOW_LINKS_FILEVISITOPTIONS = ImmutableUtils
			.singletonSet(FileVisitOption.FOLLOW_LINKS);
	private static final Set<FileVisitOption> DONT_FOLLOW_LINKS_FILEVISITOPTIONS = Collections.emptySet();
	private static final EnumSet<StandardOpenOption> LOCKFILE_OPENOPTIONS = EnumSet.of(StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

	@SuppressWarnings("unchecked")
	private static final WeakReference<FileEventListener>[] EMPTY_WEAK_REFERENCES_ARRAY = (WeakReference<FileEventListener>[]) new WeakReference<?>[] {};
	private static final DirectoryWatchEntry[] EMPTY_DIRECTORY_WATCH_ENTRIES_ARRAY = {};

	private static final class RecursiveDeletorFileVisitor implements FileVisitor<Path> {
		private final Path directory;
		protected int deleteCount = 0;

		private RecursiveDeletorFileVisitor(Path directory) {
			this.directory = directory;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			boolean deletedsomething = localFileSystemProvider.deleteIfExists(file);
			if (deletedsomething) {
				++deleteCount;
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			if (file.equals(directory)) {
				if (exc instanceof NoSuchFileException) {
					//directory doesn't exist
					return FileVisitResult.TERMINATE;
				}
			}
			throw exc;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			boolean deletedsomething = localFileSystemProvider.deleteIfExists(dir);
			if (deletedsomething) {
				++deleteCount;
			}
			return FileVisitResult.CONTINUE;
		}
	}

	private static final class RecursiveDirectoryEntryCollectorFileVisitor extends SimpleFileVisitor<Path> {
		private final NavigableMap<SakerPath, ? super FileEntry> result;
		private SakerPath currentResolve;
		private boolean firstVisit = true;

		private RecursiveDirectoryEntryCollectorFileVisitor(SakerPath resolve,
				NavigableMap<SakerPath, ? super FileEntry> result) {
			this.result = result;
			this.currentResolve = resolve;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (firstVisit) {
				throw new NotDirectoryException(file.toString());
			}
			String filename = file.getFileName().toString();
			SakerPath p = currentResolve.resolve(filename);
			result.put(p, new FileEntry(attrs));
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (firstVisit) {
				//the first dir is the dir that the file walking is applied to
				//it should not be added to the resolution path
				firstVisit = false;
				return FileVisitResult.CONTINUE;
			}
			String filename = dir.getFileName().toString();
			SakerPath p = currentResolve.resolve(filename);
			result.put(p, new FileEntry(attrs));
			currentResolve = p;
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			currentResolve = currentResolve.getParent();
			return FileVisitResult.CONTINUE;
		}
	}

	private static final class LocalFileSakerLock implements SakerFileLock {
		private final CloseableReference<?> reference;
		private final FileChannel channel;
		private FileLock lock;

		private Semaphore accessLock = new Semaphore(1);

		private LocalFileSakerLock(FileChannel channel) {
			this.channel = channel;

			reference = new CloseableReference<>(this, channel, gcQueue);
			gcReferences.add(reference);
			gcThread.get();
		}

		@Override
		public void close() throws IOException {
			accessLock.acquireUninterruptibly();
			try {
				//to wake up the GC thread
				if (gcReferences.remove(reference)) {
					reference.enqueue();
				}
				FileLock lock = this.lock;
				if (lock != null) {
					lock.release();
					this.lock = null;
				}
				channel.close();
			} finally {
				accessLock.release();
			}
		}

		@Override
		public void lock() throws IOException {
			accessLock.acquireUninterruptibly();
			try {
				if (this.lock != null) {
					throw new IllegalStateException("Trying to acquire lock more than once.");
				}
				lock = channel.lock();
			} finally {
				accessLock.release();
			}
		}

		@Override
		public boolean tryLock() throws IOException {
			accessLock.acquireUninterruptibly();
			try {
				if (this.lock != null) {
					throw new IllegalStateException("Trying to acquire lock more than once.");
				}
				FileLock nlock = channel.tryLock();
				lock = nlock;
				return nlock != null;
			} finally {
				accessLock.release();
			}
		}

		@Override
		public void release() throws IOException {
			accessLock.acquireUninterruptibly();
			try {
				FileLock lock = this.lock;
				if (lock == null) {
					throw new IllegalStateException("Lock is not acquired.");
				}
				lock.release();
				this.lock = null;
			} finally {
				accessLock.release();
			}
		}
	}

	//XXX remove this deprecation suppression if the class is handled
	@SuppressWarnings("deprecation")
	private static final class CachingSettings {
		//TODO undeprecate the property names in PropertyNames when reimplemented
		private static final Set<UUID> disabledProviders = new TreeSet<>();
		private static final Path cacheDirectory;

		private static final Map<UUID, RemoteCacheFile> remoteProviderCacheFiles = new TreeMap<>();

		static {
			String disableds = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_FILES_DISABLE_REMOTE_CACHE);
			if (disableds != null) {
				String[] splits = disableds.split("[,]+");
				for (String uuid : splits) {
					if (uuid.isEmpty()) {
						continue;
					}
					disabledProviders.add(UUID.fromString(uuid));
				}
			}
			String cachedir = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_FILES_REMOTE_CACHE_DIRECTORY);
			if (cachedir != null) {
				if (cachedir.isEmpty()) {
					cacheDirectory = null;
				} else {
					cacheDirectory = localFileSystem.getPath(cachedir);
					createCacheDirectory();
				}
			} else {
				cachedir = System.getProperty("java.io.tmpdir", "");
				if (cachedir.isEmpty()) {
					cacheDirectory = null;
				} else {
					cacheDirectory = localFileSystem.getPath(cachedir, ".sakercache");
					createCacheDirectory();
				}
			}
		}

		private static void createCacheDirectory() {
			try {
				Files.createDirectories(cacheDirectory);
			} catch (IOException e) {
				//XXX maybe handle exception?
				e.printStackTrace();
			}
		}

		public static boolean shouldCache(UUID uuid) {
			return cacheDirectory != null && !disabledProviders.contains(uuid);
		}

		public static SakerFileProvider getCachingFileProvider(UUID uuid, SakerFileProvider fp,
				RootFileProviderKey providerkey) {
			if (!shouldCache(uuid)) {
				return fp;
			}
			synchronized (CachingSettings.class) {
				RemoteCacheFile cfile = remoteProviderCacheFiles.get(uuid);
				if (cfile != null) {
					return new CachingFileProvider(cfile, fp, providerkey);
				}
				Path filepath = cacheDirectory.resolve(uuid.toString() + ".cache");
				try {
					cfile = new RemoteCacheFile(filepath, fp);
					remoteProviderCacheFiles.put(uuid, cfile);
					return new CachingFileProvider(cfile, fp, providerkey);
				} catch (IOException e) {
					e.printStackTrace();
					return fp;
				}
			}
		}
	}

	/**
	 * {@link RootFileProviderKey} implementation for {@link LocalFileProvider}.
	 */
	@ExcludeApi
	@RMIWriter(SerializeRMIObjectWriteHandler.class)
	public static final class LocalFilesKey implements RootFileProviderKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private UUID uuid;

		/**
		 * For {@link Externalizable}.
		 */
		public LocalFilesKey() {
		}

		protected LocalFilesKey(UUID uuid) {
			this.uuid = uuid;
		}

		@Override
		public UUID getUUID() {
			return uuid;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + uuid.hashCode();
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
			LocalFilesKey other = (LocalFilesKey) obj;
			if (uuid == null) {
				if (other.uuid != null)
					return false;
			} else if (!uuid.equals(other.uuid))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return uuid.toString();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(uuid);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			uuid = (UUID) in.readObject();
		}
	}

	private final class DeleteChildrenIfNotInFileVisitor extends SimpleFileVisitor<Path> {
		private final Set<String> childFileNames;
		private final Path path;
		private final Set<String> deleted;

		private DeleteChildrenIfNotInFileVisitor(Path ppath, Set<String> childfilenames, Set<String> deleted) {
			this.childFileNames = childfilenames;
			this.path = ppath;
			this.deleted = deleted;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (!file.equals(path)) {
				String filename = file.getFileName().toString();
				if (!childFileNames.contains(filename)) {
					deleteRecursivelyImpl(file);
					deleted.add(filename);
				}
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (!dir.equals(path)) {
				String filename = dir.getFileName().toString();
				if (!childFileNames.contains(filename)) {
					deleteRecursivelyImpl(dir);
					deleted.add(filename);
				}
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			throw new PartiallyDeletedChildrenException(exc, SakerPath.valueOf(file), deleted);
		}
	}

	private static final class SubDirectoryNamesFileVisitor extends SimpleFileVisitor<Path> {
		private final Set<String> result;
		private final Path ppath;

		private SubDirectoryNamesFileVisitor(Set<String> result, Path ppath) {
			this.result = result;
			this.ppath = ppath;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (!dir.equals(ppath)) {
				result.add(dir.getFileName().toString());
			}
			return FileVisitResult.SKIP_SUBTREE;
		}
	}

	private static final class DirectoryEntryCollectorFileVisitor extends SimpleFileVisitor<Path> {
		private final Map<String, FileEntry> fileNames;
		private boolean firstVisit = true;

		private DirectoryEntryCollectorFileVisitor(Map<String, FileEntry> filenames) {
			this.fileNames = filenames;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (firstVisit) {
				firstVisit = false;
				return FileVisitResult.CONTINUE;
			}
			String filename = dir.getFileName().toString();
			FileEntry entry = new FileEntry(attrs);
			fileNames.put(filename, entry);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (firstVisit) {
				throw new NotDirectoryException(file.toString());
			}
			String filename = file.getFileName().toString();
			FileEntry entry = new FileEntry(attrs);
			fileNames.put(filename, entry);
			return FileVisitResult.CONTINUE;
		}
	}

	private static class DirectoryWatchEntry {
		protected static final Consumer<FileEventListener> LISTENER_ABANDONER = FileEventListener::listenerAbandoned;
		@SuppressWarnings("unchecked")
		protected static final AtomicReferenceFieldUpdater<LocalFileProvider.DirectoryWatchEntry, WeakReference<FileEventListener>[]> ARFU_listeners = (AtomicReferenceFieldUpdater<LocalFileProvider.DirectoryWatchEntry, WeakReference<FileEventListener>[]>) (AtomicReferenceFieldUpdater<?, ?>) AtomicReferenceFieldUpdater
				.newUpdater(LocalFileProvider.DirectoryWatchEntry.class, WeakReference[].class, "listeners");

		protected final ThreadWorkPool taskPool;
		protected final boolean subTreeWatching;
		protected final WatchKey pollWatchKey;

		protected volatile WeakReference<FileEventListener>[] listeners;

		protected final PendingEvents<ConcurrentPrependAccumulator<Consumer<FileEventListener>>> pendingListenerCalls;

		@SuppressWarnings("unchecked")
		public DirectoryWatchEntry(WatchKey pollWatchKey, WatcherThread watchthread, boolean subTreeWatching,
				WeakReference<FileEventListener> firstlistener) {
			this.pollWatchKey = pollWatchKey;
			this.subTreeWatching = subTreeWatching;
			this.taskPool = watchthread.taskPool;
			Objects.requireNonNull(taskPool, "taskpool");
			this.listeners = (WeakReference<FileEventListener>[]) new WeakReference<?>[] { firstlistener };
			pendingListenerCalls = new PendingEvents<>(new ConcurrentPrependAccumulator<>(), pe -> {
				ConcurrentPrependAccumulator<Consumer<FileEventListener>> events = pe.events;
				for (Consumer<FileEventListener> r; (r = events.take()) != null;) {
					callListenersOnlyImpl(r);
				}
			});
		}

		public void deliverListenerCalls() {
			pendingListenerCalls.deliverAll();
		}

		private void postCallListeners(Consumer<FileEventListener> function) {
			if (listeners.length == 0) {
				return;
			}
			pendingListenerCalls.events.add(function);
			pendingListenerCalls.dispatch(taskPool);
		}

		private void postCallListenersAbandoned() {
			if (listeners.length == 0) {
				return;
			}
			pendingListenerCalls.events.add(LISTENER_ABANDONER);
			pendingListenerCalls.dispatch(taskPool);
		}

		private void callListenersOnlyImpl(Consumer<FileEventListener> function) {
			if (function == LISTENER_ABANDONER) {
				//when the abandon listener call is posted, the caller will cancel the watch key automatically
				WeakReference<FileEventListener>[] listeners = ARFU_listeners.getAndSet(this,
						EMPTY_WEAK_REFERENCES_ARRAY);
				for (int i = 0; i < listeners.length; i++) {
					FileEventListener l = listeners[i].get();
					if (l != null) {
						l.listenerAbandoned();
					}
				}
			} else {
				WeakReference<FileEventListener>[] listeners = this.listeners;
				for (int i = 0; i < listeners.length; i++) {
					FileEventListener l = listeners[i].get();
					if (l != null) {
						function.accept(l);
					}
				}
			}
		}

		public void created(String filename) {
			postCallChangedListener(filename);
		}

		public void deleted(String filename) {
			postCallChangedListener(filename);
		}

		public void modified(String filename) {
			postCallChangedListener(filename);
		}

		public void overflown() {
			postCallListeners(FileEventListener::eventsMissed);
		}

		public void abandoned() {
			postCallListenersAbandoned();
		}

		private void postCallChangedListener(String filename) {
			postCallListeners(l -> l.changed(filename));
		}
	}

	private static class PendingEvents<CollType> {
		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<PendingEvents> AIFU_dispatchPosted = AtomicIntegerFieldUpdater
				.newUpdater(PendingEvents.class, "dispatchPosted");

		protected final CollType events;
		private final Consumer<? super PendingEvents<CollType>> eventExecutorRunnable;

		@SuppressWarnings("unused")
		private volatile int dispatchPosted = 0;

		public PendingEvents(CollType events, Consumer<? super PendingEvents<CollType>> eventExecutorRunnable) {
			this.events = events;
			this.eventExecutorRunnable = eventExecutorRunnable;
		}

		public void dispatch(ThreadWorkPool workpool) {
			if (AIFU_dispatchPosted.compareAndSet(this, 0, 1)) {
				workpool.offer(this::run);
			}
		}

		private void run() {
			//dispatch most of the events
			eventExecutorRunnable.accept(this);
			AIFU_dispatchPosted.compareAndSet(this, 1, 0);
			//dispatch any remaining events due to possible race condition
			//this usually doesn't do anything
			eventExecutorRunnable.accept(this);
		}

		public void deliverAll() {
			eventExecutorRunnable.accept(this);
		}
	}

	private static class SubTreeFailedWatchKey implements WatchKey {
		static final SubTreeFailedWatchKey INSTANCE = new SubTreeFailedWatchKey();

		@Override
		public boolean isValid() {
			return false;
		}

		@Override
		public List<WatchEvent<?>> pollEvents() {
			return null;
		}

		@Override
		public boolean reset() {
			return false;
		}

		@Override
		public void cancel() {
		}

		@Override
		public Watchable watchable() {
			return null;
		}

		@Override
		public String toString() {
			return getClass().toString();
		}
	}

	private static class SubTreeWatchKeyState {
		public static final SubTreeWatchKeyState INSTANCE_SUBTREE_WATCHING_FAILED = new SubTreeWatchKeyState(
				SubTreeFailedWatchKey.INSTANCE, EMPTY_DIRECTORY_WATCH_ENTRIES_ARRAY);
		protected static final AtomicReferenceFieldUpdater<LocalFileProvider.SubTreeWatchKeyState, DirectoryWatchEntry[]> ARFU_dirWatchEntries = AtomicReferenceFieldUpdater
				.newUpdater(LocalFileProvider.SubTreeWatchKeyState.class, DirectoryWatchEntry[].class,
						"dirWatchEntries");

		protected final WatchKey watchKey;

		protected volatile DirectoryWatchEntry[] dirWatchEntries;

		/**
		 * @deprecated Don't use, only for the static watch failed instance.
		 */
		@Deprecated
		private SubTreeWatchKeyState(WatchKey watchKey, DirectoryWatchEntry[] dirWatchEntries) {
			this.watchKey = watchKey;
			this.dirWatchEntries = dirWatchEntries;
		}

		public SubTreeWatchKeyState(WatchKey watchKey, DirectoryWatchEntry initial) {
			this(watchKey, new DirectoryWatchEntry[] { initial });
		}

		public boolean addWatchEntry(DirectoryWatchEntry entry) {
			while (true) {
				DirectoryWatchEntry[] entries = dirWatchEntries;
				if (entries.length == 0) {
					return false;
				}
				DirectoryWatchEntry[] nentries = ArrayUtils.appended(entries, entry);
				if (ARFU_dirWatchEntries.compareAndSet(this, entries, nentries)) {
					return true;
				}
			}
		}

		/**
		 * @return <code>true</code>, if there are no more entries in this subtree watcher, and it should be cancelled.
		 */
		public boolean removeWatchEntry(DirectoryWatchEntry entry) {
			while (true) {
				DirectoryWatchEntry[] entries = dirWatchEntries;
				if (entries.length == 0) {
					return true;
				}
				int idx = ArrayUtils.arrayIndexOf(entries, entry);
				if (idx < 0) {
					return false;
				}
				if (entries.length == 1) {
					if (ARFU_dirWatchEntries.compareAndSet(this, entries, EMPTY_DIRECTORY_WATCH_ENTRIES_ARRAY)) {
						return true;
					}
					continue;
				}
				DirectoryWatchEntry[] nentries = ArrayUtils.removedAtIndex(entries, idx);
				if (ARFU_dirWatchEntries.compareAndSet(this, entries, nentries)) {
					return false;
				}
				//try again
				continue;
			}
		}
	}

	private static class WatcherThread extends Thread {
		private final WatchService watcher;
		private final WatchRegisterer registerer;

		private Map<WatchKey, Object> dispatchKeyLocks = new ConcurrentHashMap<>();
		private Map<Path, SubTreeWatchKeyState> subtreeWatchKeys = new ConcurrentSkipListMap<>();
		private ThreadWorkPool taskPool;

		private final ConcurrentNavigableMap<Path, DirectoryWatchEntry> directoryWatchers = new ConcurrentSkipListMap<>();

		private final ConcurrentSkipListMap<Path, Object> registerLocks = new ConcurrentSkipListMap<>();

		private Reference<LocalFileProvider> fpref;
		private Object startNotifyLock;

		public WatcherThread(ThreadGroup threadgroup, WatchService watcher, WatchRegisterer registerer,
				Reference<LocalFileProvider> fpref, Object startnotifylock) {
			super(threadgroup, "LocalFileProvider-watcher");
			//set the context classloader, to prevent memory leaks from keeping a reference to it.
			setContextClassLoader(null);
			this.watcher = watcher;
			this.registerer = registerer;
			this.fpref = fpref;
			this.startNotifyLock = startnotifylock;

			setDaemon(true);
		}

		@Override
		public void run() {
			Reference<LocalFileProvider> fpref = this.fpref;
			this.fpref = null;

			CloseableReference<?> gcref = createGCReference(fpref);
			if (gcref == null) {
				return;
			}
			gcReferences.add(gcref);
			gcThread.get();

			try (ThreadWorkPool workpool = ThreadUtils.newFixedWorkPool("Watcher dispatcher-")) {
				try (WatchService watcher = this.watcher) {
					this.taskPool = workpool;
					synchronized (startNotifyLock) {
						startNotifyLock.notifyAll();
						startNotifyLock = null;
					}
					while (true) {
						WatchKey key;
						try {
							key = watcher.take();
						} catch (InterruptedException e) {
							//the thread may be interrupted if the file provider was garbage collected
							//and the gc ref is closed
							//cleanly exit then
							if (fpref.get() == null) {
								break;
							}
							//exit if we were just interrupted
							return;
						}
						if (fpref.get() == null) {
							break;
						}
						workpool.offer(() -> {
							flushEvents(key);
							if (!key.reset()) {
								dispatchAbandonedEvent(key);
								handleKeyResetFailure(key);
							}
						});
					}
				} catch (ClosedWatchServiceException e) {
				} catch (IOException e) {
					e.printStackTrace();
				}
				for (DirectoryWatchEntry entry : directoryWatchers.values()) {
					entry.abandoned();
				}
				directoryWatchers.clear();
				subtreeWatchKeys.clear();
				dispatchKeyLocks.clear();
			} finally {
				if (gcReferences.remove(gcref)) {
					gcref.enqueue();
				}
			}
		}

		private CloseableReference<LocalFileProvider> createGCReference(Reference<LocalFileProvider> fpref) {
			LocalFileProvider fp = fpref.get();
			if (fp == null) {
				return null;
			}
			return new CloseableReference<>(fp, new Closeable() {
				@Override
				public void close() throws IOException {
					interrupt();
				}

				@Override
				public String toString() {
					return "WatcherThreadInterruptor";
				}
			}, gcQueue);
		}

		private Object getDispatchLockForKey(WatchKey key) {
			return dispatchKeyLocks.computeIfAbsent(key, Functionals.objectComputer());
		}

		private void flushEvents(DirectoryWatchEntry wentry) {
			flushEventsImpl(wentry.pollWatchKey, (Path) wentry.pollWatchKey.watchable(), wentry.subTreeWatching);
		}

		private void flushEvents(WatchKey key) {
			//poll the key until events are available
			//sometimes events arrive with multiple subsequent polling
			//e.g. the following sub directory and contents deletion:
			// key/
			//    sub/
			//       file.txt
			//poll 1: delete file.txt
			//poll 2: modify sub, delete sub

			Path path = (Path) key.watchable();
			boolean subtreewatching = isSubtreeWatchPath(path);
			flushEventsImpl(key, path, subtreewatching);
		}

		private void flushEventsImpl(WatchKey key, Path path, boolean subtreewatching) {
			synchronized (getDispatchLockForKey(key)) {
				if (subtreewatching) {
					//dispatch the remaining events in the pending buffer
					while (true) {
						List<WatchEvent<?>> events = key.pollEvents();
						if (events.isEmpty()) {
							break;
						}
						for (WatchEvent<?> event : events) {
							dispatchSubtreeDirectoryEvent(path, event);
						}
					}
				} else {
					DirectoryWatchEntry wentry = directoryWatchers.get(path);
					if (wentry == null) {
						//events can be ignored as no listeners or cached entries are present
						clearAllEventsFromKey(key);
						return;
					}
					while (true) {
						List<WatchEvent<?>> events = key.pollEvents();
						if (events.isEmpty()) {
							break;
						}
						for (WatchEvent<?> event : events) {
							dispatchDirectDirectoryEvent(wentry, event);
						}
					}
				}
			}
		}

		private static void clearAllEventsFromKey(WatchKey key) {
			while (true) {
				List<WatchEvent<?>> events = key.pollEvents();
				if (events.isEmpty()) {
					break;
				}
			}
		}

		private void handleKeyResetFailure(WatchKey key) {
			if (TestFlag.ENABLED) {
				TestFlag.metric().pathWatchingCancelled((Path) key.watchable());
			}
			dispatchKeyLocks.remove(key);
			Path path = (Path) key.watchable();
			SubTreeWatchKeyState subtreestate = subtreeWatchKeys.get(path);
			if (subtreestate != null && subtreestate != SubTreeWatchKeyState.INSTANCE_SUBTREE_WATCHING_FAILED) {
				subtreeWatchKeys.remove(path, subtreestate);
			}
			key.cancel();
		}

		private boolean isSubtreeWatchPath(Path path) {
			if (path.getNameCount() == 1) {
				SubTreeWatchKeyState got = subtreeWatchKeys.get(path);
				return got != null && got != SubTreeWatchKeyState.INSTANCE_SUBTREE_WATCHING_FAILED;
			}
			return false;
		}

		private void dispatchDirectDirectoryEvent(DirectoryWatchEntry wentry, WatchEvent<?> event) {
			Kind<?> kind = event.kind();
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				Path evpath = (Path) event.context();
				wentry.created(evpath.getFileName().toString());
			} else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
				Path evpath = (Path) event.context();
				wentry.deleted(evpath.getFileName().toString());
			} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
				Path evpath = (Path) event.context();
				wentry.modified(evpath.getFileName().toString());
			} else if (kind == StandardWatchEventKinds.OVERFLOW) {
				wentry.overflown();
			}
		}

		private void dispatchAbandonedEvent(WatchKey key) {
			Path path = (Path) key.watchable();
			//no need to lock the dispatching, as no pollEvent takes place and reset failure is a terminal event
			if (isSubtreeWatchPath(path)) {
				dispatchSubtreeAbandonedEvent(path);
			} else {
				DirectoryWatchEntry wentry = directoryWatchers.get(path);
				if (wentry != null) {
					dispatchDirectDirectoryAbandonedEvent(wentry);
				}
			}
		}

		private void dispatchDirectDirectoryAbandonedEvent(DirectoryWatchEntry wentry) {
			wentry.abandoned();
		}

		private void dispatchSubtreeDirectoryEvent(Path path, WatchEvent<?> event) {
			Kind<?> kind = event.kind();
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				Path evpath = (Path) event.context();
				Path filepath = path.resolve(evpath);
				Path parentdir = filepath.getParent();
				DirectoryWatchEntry wentry = directoryWatchers.get(parentdir);
				if (wentry != null) {
					wentry.created(evpath.getFileName().toString());
				}
			} else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
				Path evpath = (Path) event.context();
				Path filepath = path.resolve(evpath);
				Path parentdir = filepath.getParent();
				DirectoryWatchEntry wentry = directoryWatchers.get(parentdir);
				if (wentry != null) {
					wentry.deleted(evpath.getFileName().toString());
				}
			} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
				Path evpath = (Path) event.context();
				Path filepath = path.resolve(evpath);
				Path parentdir = filepath.getParent();
				DirectoryWatchEntry wentry = directoryWatchers.get(parentdir);
				if (wentry != null) {
					wentry.modified(evpath.getFileName().toString());
				}
			} else if (kind == StandardWatchEventKinds.OVERFLOW) {
				for (Entry<Path, DirectoryWatchEntry> entry : directoryWatchers.tailMap(path).entrySet()) {
					if (entry.getKey().startsWith(path)) {
						entry.getValue().overflown();
					} else {
						break;
					}
				}
			}
		}

		private void dispatchSubtreeAbandonedEvent(Path path) {
			for (Iterator<Entry<Path, DirectoryWatchEntry>> it = directoryWatchers.tailMap(path).entrySet()
					.iterator(); it.hasNext();) {
				Entry<Path, DirectoryWatchEntry> entry = it.next();
				if (entry.getKey().startsWith(path)) {
					it.remove();
					entry.getValue().abandoned();
				} else {
					break;
				}
			}
		}

		private Object getRegisterLock(Path path) {
			return registerLocks.computeIfAbsent(path, Functionals.objectComputer());
		}

		private DirectoryWatchEntry getOrInstallDirectoryWatcher(Path path,
				WeakReference<FileEventListener> listenerreference) throws IOException {
			DirectoryWatchEntry wentry = directoryWatchers.get(path);
			if (wentry != null) {
				if (addListenerToWatchEntryImpl(listenerreference, wentry)) {
					return wentry;
				}
				//failed to add the listener to the watch entry, as it is being invalidated
				//continue by trying to install one again
			}
			if (!SUBTREE_AVAILABLE) {
				return installWatcher(path, listenerreference);
			}
			if (TestFlag.ENABLED) {
				if (!TestFlag.metric().isSubtreeWatchingEnabled()) {
					return installWatcher(path, listenerreference);
				}
			}
			//add a directory listener
			int namecount = path.getNameCount();
			switch (namecount) {
				case 0: {
					//path is a root path like c: or /
					return installWatcher(path, listenerreference);
				}
				case 1: {
					//path is directly under root path like c:\folder or /folder
					//in this case we can try to install a subtree listener
					return installSubTreeWatcher(path, path, listenerreference);
				}
				default: {
					//path has more than 1 names, subtree watching is viable
					Path subtreepath = path.getRoot().resolve(path.getName(0));
					return installSubTreeWatcher(path, subtreepath, listenerreference);
				}
			}
		}

		private DirectoryWatchEntry installSubTreeWatcher(Path path, Path subtreepath,
				WeakReference<FileEventListener> firstlistener) throws IOException {
			SubTreeWatchKeyState presentsubtreekey = subtreeWatchKeys.get(subtreepath);
			if (presentsubtreekey != null) {
				DirectoryWatchEntry wentry = installSubTreeWatcherHandleFoundSubTreeKey(path, presentsubtreekey,
						firstlistener);
				if (wentry != null) {
					return wentry;
				}
			}
			//try installing a parent subtree watcher
			synchronized (getRegisterLock(subtreepath)) {
				//try the query again to ensure thread safety
				presentsubtreekey = subtreeWatchKeys.get(subtreepath);
				if (presentsubtreekey != null) {
					DirectoryWatchEntry wentry = installSubTreeWatcherHandleFoundSubTreeKey(path, presentsubtreekey,
							firstlistener);
					if (wentry != null) {
						return wentry;
					}
				}
				try {
					WatchKey key = registerer.register(subtreepath, ALL_EVENT_KINDS, SUBTREE_MODIFIERS);
					if (TestFlag.ENABLED) {
						TestFlag.metric().pathWatchingRegistered(subtreepath, ALL_EVENT_KINDS, SUBTREE_MODIFIERS);
					}
					//successfully registered a subtree watcher
					DirectoryWatchEntry entry = new DirectoryWatchEntry(key, this, true, firstlistener);
					SubTreeWatchKeyState prevkey = subtreeWatchKeys.put(subtreepath,
							new SubTreeWatchKeyState(key, entry));
					if (prevkey != presentsubtreekey) {
						throw new AssertionError(
								"Concurrency error, multiple concurrent sub-tree file watchers installed.");
					}
					while (true) {
						DirectoryWatchEntry preventry = directoryWatchers.putIfAbsent(path, entry);
						if (preventry == null) {
							return entry;
						}
						if (addListenerToWatchEntryImpl(firstlistener, preventry)) {
							return preventry;
						}
						if (directoryWatchers.replace(path, preventry, entry)) {
							return entry;
						}
						continue;
					}
				} catch (NotDirectoryException e) {
					throw e;
				} catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
					subtreeWatchKeys.putIfAbsent(subtreepath, SubTreeWatchKeyState.INSTANCE_SUBTREE_WATCHING_FAILED);
				}
			}
			return installWatcher(path, firstlistener);
		}

		private DirectoryWatchEntry installSubTreeWatcherHandleFoundSubTreeKey(Path path,
				SubTreeWatchKeyState subtreekey, WeakReference<FileEventListener> firstlistener) throws IOException {
			if (subtreekey == SubTreeWatchKeyState.INSTANCE_SUBTREE_WATCHING_FAILED) {
				//can't apply subtree watching
				return installWatcher(path, firstlistener);
			}
			//already watching a parent subtree
			DirectoryWatchEntry entry = new DirectoryWatchEntry(subtreekey.watchKey, this, true, firstlistener);
			if (!subtreekey.addWatchEntry(entry)) {
				return null;
			}
			while (true) {
				DirectoryWatchEntry preventry = directoryWatchers.putIfAbsent(path, entry);
				if (preventry == null) {
					return entry;
				}
				if (addListenerToWatchEntryImpl(firstlistener, preventry)) {
					return preventry;
				}
				if (directoryWatchers.replace(path, preventry, entry)) {
					return entry;
				}
				continue;
			}
		}

		private DirectoryWatchEntry installWatcher(Path path, WeakReference<FileEventListener> firstlistener)
				throws IOException {
			synchronized (getRegisterLock(path)) {
				DirectoryWatchEntry wentry = directoryWatchers.get(path);
				if (wentry != null) {
					if (addListenerToWatchEntryImpl(firstlistener, wentry)) {
						return wentry;
					}
				}

				WatchKey key = registerer.register(path, ALL_EVENT_KINDS);
				if (TestFlag.ENABLED) {
					TestFlag.metric().pathWatchingRegistered(path, ALL_EVENT_KINDS);
				}
				DirectoryWatchEntry entry = new DirectoryWatchEntry(key, this, false, firstlistener);

				DirectoryWatchEntry preventry = directoryWatchers.put(path, entry);
				if (preventry != null) {
					throw new AssertionError("Illegal state, watcher for directory is installed concurrently: " + path);
				}
				return entry;
			}
		}

		private class WatcherListenerToken extends WeakReferencedToken<FileEventListener>
				implements FileEventListener.ListenerToken {
			private final DirectoryWatchEntry watchEntry;

			@SuppressWarnings("unused")
			private final LocalFileProvider providerStrongRef;
			private final CloseableReference<?> gcRef;

			public WatcherListenerToken(DirectoryWatchEntry watchEntry,
					WeakReference<FileEventListener> listenerWeakReference, LocalFileProvider providerRef) {
				super(listenerWeakReference);
				this.watchEntry = watchEntry;
				this.providerStrongRef = providerRef;

				gcRef = new CloseableReference<>(this,
						getListenerTokenCloser(watchEntry, listenerWeakReference, WatcherThread.this), gcQueue);
				gcReferences.add(gcRef);
				gcThread.get();
			}

			@Override
			public void removeListener() {
				if (!gcReferences.remove(gcRef)) {
					//already removed
					return;
				}
				gcRef.enqueue();

				removeListenerImpl(watchEntry, objectWeakRef);
			}

			private void removeFromCollection() {
				removeListenerFromWatchEntryImpl(watchEntry, objectWeakRef);
			}
		}

		private static Closeable getListenerTokenCloser(DirectoryWatchEntry watchEntry,
				WeakReference<FileEventListener> listenerWeakReference, WatcherThread wthread) {
			return new Closeable() {
				@Override
				public void close() throws IOException {
					wthread.removeListenerImpl(watchEntry, listenerWeakReference);
				}

				@Override
				public String toString() {
					return "ListenerTokenRemover";
				}
			};
		}

		private void removeListenerImpl(DirectoryWatchEntry wentry,
				WeakReference<? extends FileEventListener> objectWeakRef) {
			flushEvents(wentry);
			wentry.deliverListenerCalls();
			removeListenerFromWatchEntryImpl(wentry, objectWeakRef);
		}

		@SuppressWarnings("unchecked")
		private void removeListenerFromWatchEntryImpl(DirectoryWatchEntry wentry,
				WeakReference<? extends FileEventListener> objectWeakRef) {
			while (true) {
				WeakReference<FileEventListener>[] listeners = wentry.listeners;
				int idx = ArrayUtils.arrayIndexOf(listeners, objectWeakRef);
				if (idx < 0) {
					//not found
					return;
				}
				if (listeners.length == 1) {
					if (!DirectoryWatchEntry.ARFU_listeners.compareAndSet(wentry, listeners,
							EMPTY_WEAK_REFERENCES_ARRAY)) {
						continue;
					}
					Path watchedpath = (Path) wentry.pollWatchKey.watchable();
					directoryWatchers.remove(watchedpath, wentry);
					if (wentry.subTreeWatching) {
						SubTreeWatchKeyState subtreewatchkeystate = subtreeWatchKeys.get(watchedpath);
						if (subtreewatchkeystate != null
								&& subtreewatchkeystate != SubTreeWatchKeyState.INSTANCE_SUBTREE_WATCHING_FAILED) {
							boolean shouldcancel = subtreewatchkeystate.removeWatchEntry(wentry);
							if (shouldcancel) {
								if (TestFlag.ENABLED) {
									TestFlag.metric().pathWatchingCancelled(watchedpath);
								}
								dispatchKeyLocks.remove(wentry.pollWatchKey);
								subtreeWatchKeys.remove(watchedpath, subtreewatchkeystate);
								subtreewatchkeystate.watchKey.cancel();
							}
						}
					} else {
						if (TestFlag.ENABLED) {
							TestFlag.metric().pathWatchingCancelled(watchedpath);
						}
						dispatchKeyLocks.remove(wentry.pollWatchKey);
						wentry.pollWatchKey.cancel();
					}
					return;
				}
				//other listeners are persent, don't uninstall the watch key
				WeakReference<FileEventListener>[] narray = ArrayUtils.removedAtIndex(listeners, idx);
				if (DirectoryWatchEntry.ARFU_listeners.compareAndSet(wentry, listeners, narray)) {
					return;
				}
				//try again
				continue;
			}
		}

		private static boolean addListenerToWatchEntryImpl(WeakReference<FileEventListener> listenerreference,
				DirectoryWatchEntry wentry) {
			while (true) {
				WeakReference<FileEventListener>[] listeners = wentry.listeners;
				if (listeners.length == 0) {
					return false;
				}
				WeakReference<FileEventListener>[] narray = ArrayUtils.appended(listeners, listenerreference);
				if (DirectoryWatchEntry.ARFU_listeners.compareAndSet(wentry, listeners, narray)) {
					return true;
				}
				continue;
			}
		}

		public FileEventListener.ListenerToken addFileEventListener(LocalFileProvider fp, Path path,
				FileEventListener listener) throws IOException {
			WeakReference<FileEventListener> weakref = new WeakReference<>(listener);
			DirectoryWatchEntry wentry = getOrInstallDirectoryWatcher(path, weakref);
			return new WatcherListenerToken(wentry, weakref, fp);
		}

		public void removeFileEventListeners(Iterable<? extends FileEventListener.ListenerToken> tokens)
				throws InvalidPathException {
			if (SUBTREE_AVAILABLE) {
				IdentityHashMap<WatchKey, Set<WatcherListenerToken>> keymap = new IdentityHashMap<>();
				for (FileEventListener.ListenerToken lt : tokens) {
					if (lt == null) {
						//fine to ignore
						continue;
					}
					if (!(lt instanceof WatcherListenerToken)) {
						throw new IllegalArgumentException(
								"Illegal file listener token type: " + lt.getClass().getName());
					}
					WatcherListenerToken wlt = (WatcherListenerToken) lt;

					keymap.computeIfAbsent(wlt.watchEntry.pollWatchKey, Functionals.hashSetComputer()).add(wlt);
				}
				for (Entry<WatchKey, Set<WatcherListenerToken>> entry : keymap.entrySet()) {
					flushEvents(entry.getKey());
					for (WatcherListenerToken wlt : entry.getValue()) {
						wlt.watchEntry.deliverListenerCalls();
						wlt.removeFromCollection();
					}
				}
			} else {
				for (FileEventListener.ListenerToken lt : tokens) {
					lt.removeListener();
				}
			}
		}
	}

	private static class GarbageCollectingThread extends Thread {
		private GarbageCollectingThread() {
			//XXX common thread group with the watcher thread
			super(ThreadUtils.getTopLevelThreadGroup(), "LocalFileProvider-GC");
			setContextClassLoader(null);
			setDaemon(true);
		}

		public static GarbageCollectingThread startNew() {
			//as this method can be called anytime, on any stack, do it as a priviliged action, so the access control
			// context and other references are not present in the new thread, therefore don't cause leaked references
			// to classloaders and others
			return AccessController.doPrivileged((PrivilegedAction<GarbageCollectingThread>) () -> {
				GarbageCollectingThread result = new GarbageCollectingThread();
				result.start();
				return result;
			});
		}

		@Override
		public void run() {
			try {
				while (true) {
					Reference<?> ref = gcQueue.remove(30 * 1000);
					//the file provider reference can be also enqueued in the queue, so check for instanceof
					if (ref instanceof CloseableReference<?>) {
						handleGCReference((CloseableReference<?>) ref);
					}
					if (gcReferences.isEmpty()) {
						//there are no more tracked references, we can exit the thread
						gcThread = LazySupplier.of(GarbageCollectingThread::startNew);
						if (!gcReferences.isEmpty()) {
							//restart the new thread as the references were concurrently modified
							gcThread.get();
						}
						pollRemainingReferences();
						return;
					}
					continue;
				}
			} catch (InterruptedException e) {
				gcThread = LazySupplier.of(GarbageCollectingThread::startNew);
				pollRemainingReferences();
				//do not automatically restart, as we've been interrupted
				//this shouldn't really ever happen, and is an undefined scenario, but try to handle gracefully if possible
			}
		}

		private static void pollRemainingReferences() {
			while (true) {
				Reference<?> ref = gcQueue.poll();
				if (ref == null) {
					break;
				}
				if (ref instanceof CloseableReference<?>) {
					handleGCReference((CloseableReference<?>) ref);
				}
			}
		}

		private static void handleGCReference(CloseableReference<?> ref) {
			if (!gcReferences.remove(ref)) {
				return;
			}
			Closeable closeable = ref.subject;
			if (closeable == null) {
				return;
			}
			try {
				closeable.close();
			} catch (Exception e) {
				//catch other kind of exceptions as well
				//print the stack trace to the standard error.
				e.printStackTrace();
			}
		}
	}

	private static final class CloseableReference<T> extends WeakReference<T> {
		Closeable subject;

		public CloseableReference(T referent, Closeable subject, ReferenceQueue<? super T> q) {
			super(referent, q);
			this.subject = subject;
		}

		@Override
		public boolean enqueue() {
			//clear out the closeable, as it is being closed when the reference is enqueued manually
			subject = null;
			return super.enqueue();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (subject != null ? subject : "") + "]";
		}

	}

	private static final class ReferencedInputStream extends InputStreamByteSource {
		private final CloseableReference<?> reference;

		public ReferencedInputStream(InputStream is) {
			super(is);
			reference = new CloseableReference<>(this, is, gcQueue);
			gcReferences.add(reference);
			gcThread.get();
		}

		@Override
		public void close() throws IOException {
			//to wake up the GC thread
			if (gcReferences.remove(reference)) {
				reference.enqueue();
			}
			super.close();
		}
	}

	private static final class ReferencedOutputStream extends OutputStreamByteSink {
		private final CloseableReference<?> reference;

		public ReferencedOutputStream(OutputStream os) {
			super(os);
			reference = new CloseableReference<>(this, os, gcQueue);
			gcReferences.add(reference);
			gcThread.get();
		}

		@Override
		public void close() throws IOException {
			//to wake up the GC thread
			if (gcReferences.remove(reference)) {
				reference.enqueue();
			}
			super.close();
		}
	}

	private static volatile Reference<LocalFileProvider> INSTANCE = null;
	private static final Set<CloseableReference<?>> gcReferences = ConcurrentHashMap.newKeySet();
	private static final ReferenceQueue<Object> gcQueue = new ReferenceQueue<>();
	private static volatile Supplier<GarbageCollectingThread> gcThread = LazySupplier
			.of(GarbageCollectingThread::startNew);

	/**
	 * Gets the singleton instance for the local file system.
	 * 
	 * @return The file provider instance.
	 */
	public static LocalFileProvider getInstance() {
		while (true) {
			Reference<LocalFileProvider> ref = INSTANCE;
			LocalFileProvider instance = ref == null ? null : ref.get();
			if (instance != null) {
				return instance;
			}
			synchronized (LocalFileProvider.class) {
				if (ref == INSTANCE) {
					instance = new LocalFileProviderImpl();
					INSTANCE = ReferencePolicy.createReference(instance, gcQueue);
					return instance;
				}
			}
		}
	}

	private static final FileSystem localFileSystem = FileSystems.getDefault();
	private static final FileSystemProvider localFileSystemProvider = localFileSystem.provider();

	private static final NavigableMap<String, Path> roots;
	static {
		NavigableMap<String, Path> rootpaths = new TreeMap<>();
		for (Path r : localFileSystem.getRootDirectories()) {
			//XXX we should filter out roots that are not available
			//e.g. a CD/DVD drive that has no disk inserted
			rootpaths.put(SakerPath.normalizeRoot(r.toString()), r);
		}
		roots = ImmutableUtils.unmodifiableNavigableMap(rootpaths);
	}

	private volatile Object watcherInstantiateSync = new Object();
	private volatile WatcherThread watcherThread;
	private WatchService watcher;

	LocalFileProvider() {
	}

	private static void initWatcherThread(ThreadGroup threadgroup, WeakReference<LocalFileProvider> fpref) {
		LocalFileProvider fp = fpref.get();
		if (fp == null) {
			return;
		}

		//we disable caching for everyhing here, as we haven't found a reliable way to cache file attributes while using notifications
		//can be enabled in the future
		RegisteringWatchService nativewatcher = NativeWatcherService.newInstance();
		WatchRegisterer registerer;
		if (nativewatcher != null) {
			fp.watcher = nativewatcher;
			registerer = nativewatcher;
		} else {
			try {
				fp.watcher = localFileSystem.newWatchService();
			} catch (IOException | UnsupportedOperationException e) {
				InternalBuildTraceImpl.ignoredStaticException(e);
				return;
			}
			registerer = WatchRegisterer.of(fp.watcher);
		}
		//we dont use the built-in watcher to cache data as they are unreliable by design
		//related issue: 
		//    https://bugs.openjdk.java.net/browse/JDK-8202759

		Object startnotifylock = new Object();
		synchronized (startnotifylock) {
			//as this method can be called anytime, on any stack, do it as a priviliged action, so the access control
			// context and other references are not present in the new thread, therefore don't cause leaked references
			// to classloaders and others
			WatcherThread wthread = AccessController.doPrivileged((PrivilegedAction<WatcherThread>) () -> {
				WatcherThread createdthread = new WatcherThread(threadgroup, fp.watcher, registerer, fpref,
						startnotifylock);
				//set a somewhat greater priority to the watcher thread
				createdthread.setPriority((Thread.MAX_PRIORITY + Thread.NORM_PRIORITY + 1) / 2);
				createdthread.setDaemon(true);
				createdthread.start();
				return createdthread;
			});
			try {
				startnotifylock.wait();
				fp.watcherThread = wthread;
			} catch (InterruptedException e) {
				e.printStackTrace();
				//close it just in case
				fp.watcherThread = null;
				fp.watcher = null;
				IOUtils.closeExc(fp.watcher);
			}
		}
	}

	@Override
	public FileEventListener.ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener)
			throws IOException {
		return addFileEventListenerImpl(toRealPath(directory), listener);
	}

	/**
	 * @see #addFileEventListener(SakerPath, FileEventListener)
	 */
	public FileEventListener.ListenerToken addFileEventListener(Path directory, FileEventListener listener)
			throws IOException {
		directory = requireLocalAbsolutePath(directory);

		//normalize the path to avoid different representations of the same path in collections
		return addFileEventListenerImpl(directory.normalize(), listener);
	}

	@Override
	public void removeFileEventListeners(Iterable<? extends FileEventListener.ListenerToken> listeners) {
		WatcherThread wthread = getWatcherThread();
		if (wthread != null) {
			wthread.removeFileEventListeners(listeners);
		}
	}

	private WatcherThread getWatcherThread() {
		//XXX implement watcher thread auto-shutdown if no more file listeners are present
		WatcherThread wthread = watcherThread;
		if (wthread != null) {
			return wthread;
		}
		Object sync = watcherInstantiateSync;
		if (sync == null) {
			return null;
		}
		synchronized (sync) {
			if (watcherInstantiateSync == null) {
				return null;
			}
			WeakReference<LocalFileProvider> fpref = new WeakReference<>(LocalFileProvider.this);
			ThreadGroup watchertg = new ThreadGroup(ThreadUtils.getTopLevelThreadGroup(), "Local files watcher");
			watchertg.setDaemon(true);
			initWatcherThread(watchertg, fpref);

			if (watcher == null) {
				this.watcherInstantiateSync = null;
			}
			return watcherThread;
		}
	}

	private static final WatchEvent.Kind<?>[] ALL_EVENT_KINDS = { StandardWatchEventKinds.OVERFLOW,
			StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
			StandardWatchEventKinds.ENTRY_DELETE };
	private static final WatchEvent.Modifier[] SUBTREE_MODIFIERS;
	private static final boolean SUBTREE_AVAILABLE;

	private static final LocalFilesKey PROVIDER_KEY;

	static {
		PROVIDER_KEY = new LocalFilesKey(readFileProviderKeyUUID());
	}

	private static UUID readFileProviderKeyUUID() {
		Path filepath = null;
		try {
			Path storagedir = SakerEnvironmentImpl.getDefaultStorageDirectory();
			filepath = storagedir.resolve(UUID_FILE_NAME);
			try {
				byte[] allbytes = Files.readAllBytes(filepath);
				return UUID.fromString(new String(allbytes));
			} catch (IllegalArgumentException | IOException e1) {
				//invalid contents or failed to read
				//proceed by generating a new one
				UUID uuid = UUID.randomUUID();
				Path tempfile = null;

				try {
					Files.createDirectories(storagedir);
					tempfile = filepath.resolveSibling(UUID.randomUUID() + ".temp");
					try {
						Files.write(tempfile, uuid.toString().getBytes(StandardCharsets.UTF_8));
						Files.move(tempfile, filepath);
					} catch (IOException e) {
						// was concurrently created, or other I/O error. try again anyway
						try {
							byte[] allbytes = Files.readAllBytes(filepath);
							return UUID.fromString(new String(allbytes));
						} catch (Exception e2) {
							e2.addSuppressed(e);
							throw e2;
						}
					}
				} catch (Throwable e) {
					e.addSuppressed(e1);
					if (tempfile != null) {
						try {
							Files.deleteIfExists(tempfile);
						} catch (Exception e2) {
							e.addSuppressed(e2);
						}
					}
					throw e;
				}
				return uuid;
			}
		} catch (IOException e) {
			//something failed horribly? use a random
			InternalBuildTraceImpl.ignoredStaticException(
					new IOException("Failed to retrieve local file provider UUID from: " + filepath, e));
			return UUID.randomUUID();
		}
	}

	static {
		WatchEvent.Modifier filetreemod = FileUtils.getFileTreeExtendedWatchEventModifier();
		SUBTREE_AVAILABLE = filetreemod != null;
		if (SUBTREE_AVAILABLE) {
			SUBTREE_MODIFIERS = new WatchEvent.Modifier[] { filetreemod };
		} else {
			SUBTREE_MODIFIERS = new WatchEvent.Modifier[0];
		}
	}

	/**
	 * Converts the argument absolute path to a local {@link Path} for the local file system.
	 * 
	 * @param path
	 *            The path to convert.
	 * @return The localized path.
	 * @throws InvalidPathException
	 *             If the path is not valid on the local filesystem.
	 * @throws InvalidPathFormatException
	 *             If the argument is not an absolute path.
	 * @see SakerPath#valueOf(Path)
	 */
	public static Path toRealPath(SakerPath path) throws InvalidPathException, InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(path);
		String root = path.getRoot();
		if (!roots.containsKey(root)) {
			throw new InvalidPathException(path.toString(),
					"Path root (" + root + ") is not valid on the local file system.");
		}

		String str;
		if (path.getNameCount() == 0) {
			str = root;
			if (str == null) {
				throw new InvalidPathException(path.toString(), "No root found.");
			}
			if (str.endsWith(":")) {
				//in case of single drive, Paths.get() doesn't play well with "c:" like paths
				str += "\\";
			}
		} else {
			str = path.toString();
		}
		return localFileSystem.getPath(str);
	}

	@Override
	public NavigableSet<String> getRoots() {
		return roots.navigableKeySet();
	}

	@Override
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(SakerPath path) throws IOException {
		return getDirectoryEntriesByNameImpl(toRealPath(path));
	}

	/**
	 * @see #getDirectoryEntries(SakerPath)
	 */
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);

		return getDirectoryEntriesByNameImpl(path);
	}

	@Override
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(SakerPath path)
			throws IOException {
		return getDirectoryEntriesRecursivelyImpl(toRealPath(path));
	}

	/**
	 * @see #getDirectoryEntriesRecursively(SakerPath)
	 */
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);
		return getDirectoryEntriesRecursivelyImpl(path);
	}

	@Override
	public NavigableSet<String> getDirectoryEntryNames(SakerPath path) throws IOException {
		return getDirectoryEntryNamesImpl(toRealPath(path));
	}

	/**
	 * @see #getDirectoryEntryNames(SakerPath)
	 */
	public NavigableSet<String> getDirectoryEntryNames(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);

		return getDirectoryEntryNamesImpl(path);
	}

	@Override
	public NavigableSet<String> getSubDirectoryNames(SakerPath path) throws IOException {
		Path ppath = toRealPath(path);
		return getSubDirectoryNamesImpl(ppath);
	}

	/**
	 * @see #getSubDirectoryNames(SakerPath)
	 */
	public NavigableSet<String> getSubDirectoryNames(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);

		return getSubDirectoryNamesImpl(path);
	}

	@Override
	public Entry<String, ? extends FileEntry> getDirectoryEntryIfSingle(SakerPath path) throws IOException {
		Path ppath = toRealPath(path);
		return getDirectoryEntryIfSingleImpl(ppath);
	}

	/**
	 * @see #getDirectoryEntryIfSingle(SakerPath)
	 */
	public Entry<String, ? extends FileEntry> getDirectoryEntryIfSingle(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);

		return getDirectoryEntryIfSingleImpl(path);
	}

	private static Entry<String, ? extends FileEntry> getDirectoryEntryIfSingleImpl(Path path) throws IOException {
		try (DirectoryStream<Path> stream = localFileSystemProvider.newDirectoryStream(path,
				AcceptAllFilter.INSTANCE)) {
			Iterator<Path> it = stream.iterator();
			if (!it.hasNext()) {
				return null;
			}
			Path entry = it.next();
			if (it.hasNext()) {
				//not a single entry
				return null;
			}
			//XXX here is a race condition between reading the entry, and retrieving the attributes
			FileEntry attrs = readFileEntryImpl(entry, EMPTY_LINK_OPTIONS);
			return ImmutableUtils.makeImmutableMapEntry(entry.getFileName().toString(), attrs);
		}
	}

	/**
	 * @see #writeTo(Path, ByteSink, OpenOption...)
	 */
	public long writeToStream(Path path, OutputStream os, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return writeToStreamImpl(path, os, openoptions);
	}

	/**
	 * @see #writeTo(Path, ByteSink, OpenOption...)
	 */
	//TODO doc: since 0.8.10
	public long writeToStream(SakerPath path, OutputStream os, OpenOption... openoptions) throws IOException {
		Path ppath = toRealPath(path);

		return writeToStreamImpl(ppath, os, openoptions);
	}

	/**
	 * @see #writeToFile(ByteSource, SakerPath, OpenOption...)
	 */
	public long writeToFile(InputStream is, Path path, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return writeToFileImpl(is, path, openoptions);
	}

	@Override
	public FileHashResult hash(SakerPath path, String algorithm, OpenOption... openoptions)
			throws NoSuchAlgorithmException, IOException {
		Path ppath = toRealPath(path);
		return hashImpl(ppath, algorithm, openoptions);
	}

	/**
	 * @see #hash(SakerPath, String, OpenOption...)
	 */
	public FileHashResult hash(Path path, String algorithm, OpenOption... openoptions)
			throws NoSuchAlgorithmException, IOException {
		path = requireLocalAbsolutePath(path);

		return hashImpl(path, algorithm, openoptions);
	}

	private FileHashResult hashImpl(Path path, String algorithm, OpenOption[] openoptions)
			throws NoSuchAlgorithmException, IOException {
		MessageDigest digest = MessageDigest.getInstance(algorithm);
		long count = writeTo(path, StreamUtils.toByteSink(digest), openoptions);
		return new FileHashResult(count, digest.digest());
	}

	@Override
	public long writeTo(SakerPath path, ByteSink out, OpenOption... openoptions) throws IOException {
		Path ppath = toRealPath(path);
		return writeToImpl(ppath, out, openoptions);
	}

	/**
	 * @see #writeTo(SakerPath, ByteSink, OpenOption...)
	 */
	public long writeTo(Path path, ByteSink out, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return writeToImpl(path, out, openoptions);
	}

	@Override
	public void moveFile(SakerPath source, SakerPath target, CopyOption... copyoptions) throws IOException {
		Path psource = toRealPath(source);
		Path ptarget = toRealPath(target);

		moveFileImpl(psource, ptarget, copyoptions);
	}

	/**
	 * @see #moveFile(SakerPath, SakerPath, CopyOption...)
	 */
	public void moveFile(Path source, Path target, CopyOption... copyoptions) throws IOException {
		source = requireLocalAbsolutePath(source);
		target = requireLocalAbsolutePath(target);

		moveFileImpl(source, target, copyoptions);
	}

	private static void moveFileImpl(Path source, Path target, CopyOption[] copyoptions) throws IOException {
		localFileSystemProvider.move(source, target, copyoptions);
	}

	@Override
	public ByteArrayRegion getAllBytes(SakerPath path, OpenOption... openoptions) throws IOException {
		Path ppath = toRealPath(path);
		return getAllBytesImpl(ppath, openoptions);
	}

	/**
	 * @see #getAllBytes(SakerPath, OpenOption...)
	 */
	public ByteArrayRegion getAllBytes(Path path, OpenOption... openoptions) throws IOException {
		return getAllBytesImpl(path, openoptions);
	}

	@Override
	public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException {
		Path ppath = toRealPath(path);
		return openInputImpl(ppath, openoptions);
	}

	@ExcludeApi
	public OpenedRMIBufferedFileInput openRMIBufferedInput(SakerPath path, OpenOption... openoptions)
			throws IOException {
		Path ppath = toRealPath(path);
		ReferencedInputStream inputimpl = openInputImpl(ppath, openoptions);
		try {
			ByteArrayRegion initialBytes;
			boolean closed;
			{
				byte[] bytebuf = new byte[RMIBufferedFileInputByteSource.DEFAULT_BUFFER_SIZE];
				int read = StreamUtils.readFillStreamBytes(inputimpl, bytebuf);
				initialBytes = read == 0 ? null : ByteArrayRegion.wrap(bytebuf, 0, read);
				closed = read < bytebuf.length;
			}
			if (closed) {
				inputimpl.close();
			}
			RMIBufferedFileInput input = new RMIBufferedFileInput() {
				@Override
				public RMIBufferedReadResult read(int counthint) throws IOException {
					if (counthint <= 0) {
						counthint = RMIBufferedFileInputByteSource.DEFAULT_BUFFER_SIZE;
					} else {
						counthint = counthint * 3 / 2;
					}
					byte[] buf = new byte[counthint];
					int read = StreamUtils.readFillStreamBytes(inputimpl, buf);
					boolean closed = read < buf.length;
					if (closed) {
						inputimpl.close();
					}
					if (read == 0) {
						return RMIBufferedReadResult.INSTANCE_NO_DATA_CLOSED;
					}
					return new RMIBufferedReadResult(ByteArrayRegion.wrap(buf, 0, read), closed);
				}

				@Override
				public void close() throws IOException {
					inputimpl.close();
				}
			};
			return new OpenedRMIBufferedFileInput(input, initialBytes, closed);
		} catch (Throwable e) {
			//in case of error, close the stream
			IOUtils.addExc(e, IOUtils.closeExc(inputimpl));
			throw e;
		}
	}

	@ExcludeApi
	public RMIBufferedFileOutput openRMIBufferedOutput(SakerPath path, OpenOption[] openoptions,
			MultiByteArray writecontents) throws IOException {
		Path ppath = toRealPath(path);

		return openRMIBufferedOutputImpl(ppath, openoptions, writecontents);
	}

	@ExcludeApi
	public RMIBufferedFileOutput openRMIEnsureWriteBufferedOutput(SakerPath path, OpenOption[] openoptions,
			int operationflags, MultiByteArray writecontents) throws IOException {
		Path ppath = toRealPath(path);

		ensureWriteRequestImpl(ppath, FileEntry.TYPE_FILE, operationflags);
		return openRMIBufferedOutputImpl(ppath, openoptions, writecontents);
	}

	private static RMIBufferedFileOutput openRMIBufferedOutputImpl(Path path, OpenOption[] openoptions,
			MultiByteArray writecontents) throws IOException {
		ReferencedOutputStream outputimpl = openOutputImpl(path, openoptions);
		try {
			if (writecontents != null) {
				for (ByteArrayRegion bar : writecontents.getArrays()) {
					if (bar == null) {
						continue;
					}
					bar.writeTo(outputimpl);
				}
			}
			return new RMIBufferedFileOutput() {
				@Override
				public void write(MultiByteArray bytes) throws IOException {
					if (bytes == null) {
						return;
					}
					for (ByteArrayRegion bar : bytes.getArrays()) {
						if (bar == null) {
							continue;
						}
						bar.writeTo(outputimpl);
					}
				}

				@Override
				public void flush(ByteArrayRegion bytes) throws IOException {
					if (bytes != null) {
						bytes.writeTo(outputimpl);
					}
					outputimpl.flush();
				}

				@Override
				public void close(ByteArrayRegion bytes) throws IOException {
					if (bytes != null) {
						bytes.writeTo(outputimpl);
					}
					outputimpl.close();
				}
			};
		} catch (Throwable e) {
			//in case of error, close the stream
			IOUtils.addExc(e, IOUtils.closeExc(outputimpl));
			throw e;
		}
	}

	@ExcludeApi
	public void touchRMIOpenOutput(SakerPath path, OpenOption[] openoptions, ByteArrayRegion bytes) throws IOException {
		Path ppath = toRealPath(path);
		touchRMIOpenOutputImpl(ppath, openoptions, bytes);
	}

	private static void touchRMIOpenOutputImpl(Path ppath, OpenOption[] openoptions, ByteArrayRegion bytes)
			throws IOException {
		try (ReferencedOutputStream outputimpl = openOutputImpl(ppath, openoptions)) {
			if (bytes != null) {
				bytes.writeTo(outputimpl);
			}
		}
	}

	@ExcludeApi
	public void touchRMIEnsureWriteOpenOutput(SakerPath path, OpenOption[] openoptions, int operationflags,
			ByteArrayRegion bytes) throws IOException {
		Path ppath = toRealPath(path);

		ensureWriteRequestImpl(ppath, FileEntry.TYPE_FILE, operationflags);
		touchRMIOpenOutputImpl(ppath, openoptions, bytes);
	}

	@ExcludeApi
	public RMIWriteToResult writeToRMIBuffered(SakerPath path, ByteSink out, OpenOption[] openoptions)
			throws IOException {
		Path ppath = toRealPath(path);
		Set<OpenOption> optionsset;
		if (openoptions == null) {
			optionsset = Collections.emptySet();
		} else {
			optionsset = new HashSet<>(openoptions.length);
			Collections.addAll(optionsset, openoptions);
		}
		try (FileChannel fc = localFileSystemProvider.newFileChannel(ppath, optionsset);
				InputStream is = Channels.newInputStream(fc);
				UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream(
						RMIBufferedFileOutputByteSink.DEFAULT_BUFFER_SIZE)) {
			int read = baos.readFrom(is, RMIBufferedFileOutputByteSink.DEFAULT_BUFFER_SIZE);
			if (read < RMIBufferedFileOutputByteSink.DEFAULT_BUFFER_SIZE) {
				//file is shorter
				if (RMIConnection.isRemoteObject(out)) {
					return new RMIWriteToResult(baos.toByteArrayRegion());
				}
				out.write(baos.toByteArrayRegion());
				return new RMIWriteToResult(read);
			}
			//file is longer than our default buffer size
			//check the size of the file to see if we can do it in a single network call
			long size = fc.size();
			if (size <= read) {
				//size is less that what we read? shouldn't happen, but check anyway.
				//write the already read bytes and return
				if (RMIConnection.isRemoteObject(out)) {
					return new RMIWriteToResult(baos.toByteArrayRegion());
				}
				out.write(baos.toByteArrayRegion());
				return new RMIWriteToResult(read);
			}
			if (size <= RMIBufferedFileOutputByteSink.DEFAULT_BUFFER_SIZE * 4) {
				//read the whole thing in the buffer
				baos.readFrom(is);
				if (RMIConnection.isRemoteObject(out)) {
					return new RMIWriteToResult(baos.toByteArrayRegion());
				}
				out.write(baos.toByteArrayRegion());
				return new RMIWriteToResult(baos.size());
			}
			//perform multiple reads and writes
			long result = read;
			out.write(baos.toByteArrayRegion());
			baos.reset();
			while (true) {
				read = baos.readFrom(is, RMIBufferedFileOutputByteSink.DEFAULT_BUFFER_SIZE);
				if (read < RMIBufferedFileOutputByteSink.DEFAULT_BUFFER_SIZE) {
					if (RMIConnection.isRemoteObject(out)) {
						//the last read bytes should be written by the caller 
						return new RMIWriteToResult(baos.toByteArrayRegion(), result);
					}
					result += read;
					out.write(baos.toByteArrayRegion());
					break;
				}
				result += read;
				out.write(baos.toByteArrayRegion());
				baos.reset();
			}
			return new RMIWriteToResult(result);
		}
	}

	/**
	 * @see #openInput(SakerPath, OpenOption...)
	 */
	public ByteSource openInput(Path path, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return openInputImpl(path, openoptions);
	}

	/**
	 * @see #openInput(Path, OpenOption...)
	 */
	public InputStream openInputStream(Path path, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return openInputStreamImpl(path, openoptions);
	}

	/**
	 * @see #openInput(Path, OpenOption...)
	 * @since saker.build 0.8.1
	 */
	public InputStream openInputStream(SakerPath path, OpenOption... openoptions) throws IOException {
		Path ppath = toRealPath(path);

		return openInputStreamImpl(ppath, openoptions);
	}

	@Override
	public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
		return openOutputImpl(toRealPath(path), openoptions);
	}

	@Override
	public int ensureWriteRequest(SakerPath path, int filetype, int opflag) throws IOException {
		return ensureWriteRequestImpl(toRealPath(path), filetype, opflag);
	}

	/**
	 * @see #ensureWriteRequest(SakerPath, int, int)
	 */
	public int ensureWriteRequest(Path path, int filetype, int opflag) throws IOException {
		path = requireLocalAbsolutePath(path);

		return ensureWriteRequestImpl(path, filetype, opflag);
	}

	private static int ensureWriteRequestImpl(Path path, int filetype, int opflag)
			throws IllegalArgumentException, IOException {
		switch (filetype) {
			case FileEntry.TYPE_FILE: {
				//opening an output stream to a symbolic link works, so we can follow links when reading attributes
				FileEntry attrs;
				try {
					attrs = readFileEntryImpl(path, EMPTY_LINK_OPTIONS);
				} catch (IOException e) {
					//failed to get attributes, file probably doesnt exist
					//make sure the parent directories exist
					try {
						//create the directories for the parent path, to make this path writeable
						return createDirectoriesImpl(path.getParent(), opflag);
					} catch (IOException e2) {
						e.addSuppressed(e2);
						throw e;
					}
					//unreachable
				}
				if (attrs.getType() == FileEntry.TYPE_FILE) {
					//if there is already a file, it should be overwritable
					return RESULT_NO_CHANGES;
				}
				if (((opflag
						& OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE) == OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE)) {
					//will throw an appropriate exception if the directory is not empty
					boolean anydeleted = localFileSystemProvider.deleteIfExists(path);
					if (anydeleted) {
						return RESULT_FLAG_FILES_DELETED;
					}
					return RESULT_NO_CHANGES;
				}
				//recursive deletion is allowed
				//delete the existing not file (probably directory), but do not create anything
				boolean anydeleted = deleteRecursivelyImpl(path);
				return anydeleted ? RESULT_FLAG_FILES_DELETED : RESULT_NO_CHANGES;
			}
			case FileEntry.TYPE_DIRECTORY: {
				FileEntry attrs;
				try {
					attrs = readFileEntryImpl(path, EMPTY_LINK_OPTIONS);
				} catch (IOException e) {
					//failed to get attributes, directory probably doesnt exist
					//try to create the directory
					try {
						return createDirectoriesImpl(path, opflag);
					} catch (IOException e2) {
						e2.addSuppressed(e);
						throw e2;
					}
					//unreachable
				}
				if (attrs.getType() == FileEntry.TYPE_DIRECTORY) {
					return RESULT_NO_CHANGES;
				}
				//delete the existing not directory (probably a file), and create the directory itself
				//no need to recursively delete, as the file is not a directory. 
				//    its okay to fail if it is modified concur
				boolean anydeleted;
				try {
					anydeleted = localFileSystemProvider.deleteIfExists(path);
				} catch (DirectoryNotEmptyException e) {
					//we can't delete the file, because it is a directory and not empty
					//this can happen if it was quickly concurrently modified
					//we make no changes in the end
					return RESULT_NO_CHANGES;
				}
				//call createDirectory instead of createDirectories
				//    as we know that the parent exists, as the file existed.
				//    its okay to fail if there is concurrent modification
				try {
					localFileSystemProvider.createDirectory(path);
				} catch (FileAlreadyExistsException e) {
					if (isDirectory(path, EMPTY_LINK_OPTIONS)) {
						return (anydeleted ? RESULT_FLAG_FILES_DELETED : 0);
					}
					//we fail if there was concurrent modification
					throw e;
				}
				return RESULT_FLAG_DIRECTORY_CREATED | (anydeleted ? RESULT_FLAG_FILES_DELETED : 0);
			}
			default: {
				throw new IllegalArgumentException("Invalid file type: " + filetype);
			}
		}
	}

	@Override
	public ByteSink ensureWriteOpenOutput(SakerPath path, int operationflag, OpenOption... openoptions)
			throws IOException, NullPointerException {
		Path ppath = toRealPath(path);

		return ensureWriteOpenOutputImpl(ppath, operationflag, openoptions);
	}

	/**
	 * @see #ensureWriteOpenOutput(SakerPath, int, OpenOption...)
	 */
	public ByteSink ensureWriteOpenOutput(Path path, int operationflag, OpenOption... openoptions)
			throws IOException, NullPointerException {
		path = requireLocalAbsolutePath(path);

		return ensureWriteOpenOutputImpl(path, operationflag, openoptions);
	}

	private static ByteSink ensureWriteOpenOutputImpl(Path path, int operationflag, OpenOption[] openoptions)
			throws IOException, NullPointerException {
		ensureWriteRequestImpl(path, FileEntry.TYPE_FILE, operationflag);
		return openOutputImpl(path, openoptions);
	}

	/**
	 * @see #openOutput(SakerPath, OpenOption...)
	 */
	public ByteSink openOutput(Path path, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return openOutputImpl(path, openoptions);
	}

	/**
	 * @see #openOutput(Path, OpenOption...)
	 */
	public OutputStream openOutputStream(Path path, OpenOption... openoptions) throws IOException {
		path = requireLocalAbsolutePath(path);

		return openOutputStreamImpl(path, openoptions);
	}

	/**
	 * @see #openOutput(SakerPath, OpenOption...)
	 */
	public OutputStream openOutputStream(SakerPath path, OpenOption... openoptions) throws IOException {
		return openOutputStreamImpl(toRealPath(path), openoptions);
	}

	@Override
	public FileEntry getFileAttributes(SakerPath path, LinkOption... linkoptions) throws IOException {
		return readFileEntryImpl(toRealPath(path), linkoptions);
	}

	/**
	 * @see #getFileAttributes(SakerPath, LinkOption...)
	 */
	public FileEntry getFileAttributes(Path file, LinkOption... linkoptions) throws IOException {
		file = requireLocalAbsolutePath(file);

		return readFileEntryImpl(file, linkoptions);
	}

	@Override
	public boolean setPosixFilePermissions(SakerPath path, Set<PosixFilePermission> permissions)
			throws NullPointerException, IOException {
		return setPosixFilePermissionsImpl(toRealPath(path), permissions);
	}

	/**
	 * @see #setPosixFilePermissions(SakerPath, Set)
	 */
	public boolean setPosixFilePermissions(Path path, Set<PosixFilePermission> permissions)
			throws NullPointerException, IOException {
		path = requireLocalAbsolutePath(path);

		return setPosixFilePermissionsImpl(path, permissions);
	}

	private static boolean setPosixFilePermissionsImpl(Path path, Set<PosixFilePermission> permissions)
			throws IOException {
		PosixFileAttributeView view = localFileSystemProvider.getFileAttributeView(path, PosixFileAttributeView.class);
		if (view == null) {
			return false;
		}
		view.setPermissions(permissions);
		return true;
	}

	@Override
	public boolean modifyPosixFilePermissions(SakerPath path, Set<PosixFilePermission> addpermissions,
			Set<PosixFilePermission> removepermissions) throws NullPointerException, IOException {
		return modifyPosixFilePermissionsimpl(toRealPath(path), addpermissions, removepermissions);
	}

	/**
	 * @see #modifyPosixFilePermissions(SakerPath, Set, Set)
	 */
	public boolean modifyPosixFilePermissions(Path path, Set<PosixFilePermission> addpermissions,
			Set<PosixFilePermission> removepermissions) throws NullPointerException, IOException {
		path = requireLocalAbsolutePath(path);

		return modifyPosixFilePermissionsimpl(path, addpermissions, removepermissions);
	}

	private static boolean modifyPosixFilePermissionsimpl(Path path, Set<PosixFilePermission> addpermissions,
			Set<PosixFilePermission> removepermissions) throws IOException {
		PosixFileAttributeView view = localFileSystemProvider.getFileAttributeView(path, PosixFileAttributeView.class);
		if (view == null) {
			return false;
		}
		PosixFileAttributes attrs = view.readAttributes();
		Set<PosixFilePermission> existingpermissions = attrs.permissions();
		boolean modified = false;
		if (addpermissions != null) {
			modified |= existingpermissions.addAll(addpermissions);
		}
		if (removepermissions != null) {
			modified |= existingpermissions.removeAll(removepermissions);
		}
		if (modified) {
			view.setPermissions(existingpermissions);
		}
		return true;
	}

	@Override
	public Set<PosixFilePermission> getPosixFilePermissions(SakerPath path) throws NullPointerException, IOException {
		return getPosixFilePermissionsImpl(toRealPath(path));
	}

	/**
	 * @see #getPosixFilePermissions(SakerPath)
	 */
	public Set<PosixFilePermission> getPosixFilePermissions(Path path) throws NullPointerException, IOException {
		path = requireLocalAbsolutePath(path);

		return getPosixFilePermissionsImpl(path);
	}

	private static Set<PosixFilePermission> getPosixFilePermissionsImpl(Path path) throws IOException {
		PosixFileAttributeView view = localFileSystemProvider.getFileAttributeView(path, PosixFileAttributeView.class);
		if (view == null) {
			return null;
		}
		return view.readAttributes().permissions();
	}

	@Override
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException {
		setLastModifiedMillisImpl(toRealPath(path), millis);
	}

	/**
	 * @see #setLastModifiedMillis(SakerPath, long)
	 */
	public void setLastModifiedMillis(Path path, long millis) throws IOException {
		path = requireLocalAbsolutePath(path);

		setLastModifiedMillisImpl(path, millis);
	}

	private static void setLastModifiedMillisImpl(Path path, long millis) throws IOException {
		setLastModifiedTimeImpl(path, FileTime.fromMillis(millis));
	}

	/**
	 * @see #setLastModifiedMillis(Path, long)
	 */
	public void setLastModifiedTime(Path path, FileTime time) throws IOException {
		path = requireLocalAbsolutePath(path);

		setLastModifiedTimeImpl(path, time);
	}

	private static void setLastModifiedTimeImpl(Path path, FileTime time) throws IOException {
		localFileSystemProvider.getFileAttributeView(path, BasicFileAttributeView.class).setTimes(time, null, null);
	}

	@Override
	public boolean isChanged(SakerPath path, long size, long modificationmillis, LinkOption... linkoptions) {
		Path ppath = toRealPath(path);
		return isChangedImpl(ppath, size, modificationmillis, linkoptions);
	}

	/**
	 * @see #isChanged(SakerPath, long, long, LinkOption...)
	 */
	public boolean isChanged(Path path, long size, long modificationmillis, LinkOption... linkoptions) {
		path = requireLocalAbsolutePath(path);

		return isChangedImpl(path, size, modificationmillis, linkoptions);
	}

	@Override
	public void createDirectories(SakerPath path) throws IOException {
		createDirectoriesImpl(toRealPath(path), OPERATION_FLAG_NONE);
	}

	/**
	 * @see #createDirectories(SakerPath)
	 */
	public void createDirectories(Path path) throws FileAlreadyExistsException, IOException {
		path = requireLocalAbsolutePath(path);

		createDirectoriesImpl(path, OPERATION_FLAG_NONE);
	}

	@Override
	public void deleteRecursively(SakerPath path) throws IOException {
		deleteRecursivelyImpl(toRealPath(path));
	}

	/**
	 * @see #deleteRecursively(SakerPath)
	 */
	public void deleteRecursively(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);

		deleteRecursivelyImpl(path);
	}

	@Override
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException {
		deleteImpl(toRealPath(path));
	}

	/**
	 * @see #delete(SakerPath)
	 */
	public void delete(Path path) throws IOException, DirectoryNotEmptyException {
		path = requireLocalAbsolutePath(path);
		deleteImpl(path);
	}

	private static void deleteImpl(Path path) throws IOException, DirectoryNotEmptyException {
		localFileSystemProvider.deleteIfExists(path);
	}

	@Override
	public void clearDirectoryRecursively(SakerPath path) throws IOException {
		Path ppath = toRealPath(path);
		clearDirectoryImpl(ppath);
	}

	/**
	 * @see #clearDirectoryRecursively(SakerPath)
	 */
	public void clearDirectoryRecursively(Path directory) throws IOException {
		directory = requireLocalAbsolutePath(directory);

		clearDirectoryImpl(directory);
	}

	@Override
	public int deleteRecursivelyIfNotFileType(SakerPath path, int filetype) throws IOException {
		Path file = toRealPath(path);
		return deleteIfNotFileTypeImpl(file, filetype);
	}

	/**
	 * @see #deleteRecursivelyIfNotFileType(SakerPath, int)
	 */
	public int deleteRecursivelyIfNotFileType(Path path, int filetype) throws IOException {
		path = requireLocalAbsolutePath(path);

		return deleteIfNotFileTypeImpl(path, filetype);
	}

	@Override
	public NavigableSet<String> deleteChildrenRecursivelyIfNotIn(SakerPath path, Set<String> childfilenames)
			throws IOException {
		Path ppath = toRealPath(path);
		return deleteChildrenIfNotInImpl(ppath, childfilenames);
	}

	/**
	 * @see #deleteChildrenRecursivelyIfNotIn(SakerPath, Set)
	 */
	public NavigableSet<String> deleteChildrenRecursivelyIfNotIn(Path path, Set<String> childfilenames)
			throws IOException {
		path = requireLocalAbsolutePath(path);

		return deleteChildrenIfNotInImpl(path, childfilenames);
	}

	@Override
	public RootFileProviderKey getProviderKey() {
		return getProviderKeyStatic();
	}

	/**
	 * Same as {@link #getProviderKey()}, but can be called statically.
	 * 
	 * @return The file provider key.
	 */
	public static RootFileProviderKey getProviderKeyStatic() {
		return PROVIDER_KEY;
	}

	@Override
	@ExcludeApi
	public SakerFileLock createLockFile(SakerPath path) throws IOException {
		Path ppath = toRealPath(path);
		return createLockFileImpl(ppath);
	}

	/**
	 * @see #createLockFile(SakerPath)
	 */
	@ExcludeApi
	public SakerFileLock createLockFile(Path path) throws IOException {
		path = requireLocalAbsolutePath(path);

		return createLockFileImpl(path);
	}

	/**
	 * Creates a path key for the given path and <code>this</code> file provider.
	 * 
	 * @param path
	 *            The path.
	 * @return The created path key.
	 */
	public ProviderHolderPathKey getPathKey(SakerPath path) {
		SakerPathFiles.requireAbsolutePath(path);

		return getPathKeyImpl(path);
	}

	/**
	 * Creates a path key for the given path and <code>this</code> file provider.
	 * 
	 * @param path
	 *            The path.
	 * @return The created path key.
	 */
	public ProviderHolderPathKey getPathKey(Path path) {
		path = requireLocalAbsolutePath(path);

		return getPathKeyImpl(SakerPath.valueOf(path));
	}

	private ProviderHolderPathKey getPathKeyImpl(SakerPath mpath) {
		if (!roots.containsKey(mpath.getRoot())) {
			throw new InvalidPathFormatException("Root not found: " + mpath.getRoot() + " available: " + roots);
		}
		return new SimpleProviderHolderPathKey(mpath, this, PROVIDER_KEY);
	}

	/**
	 * Creates a path key for the given path and the local file provider key.
	 * 
	 * @param path
	 *            The path.
	 * @return The created path key.
	 * @see #getProviderKeyStatic()
	 */
	public static PathKey getPathKeyStatic(SakerPath path) {
		SakerPathFiles.requireAbsolutePath(path);

		return getPathKeyStaticImpl(path);
	}

	/**
	 * Creates a path key for the given path and the local file provider key.
	 * 
	 * @param path
	 *            The path.
	 * @return The created path key.
	 * @see #getProviderKeyStatic()
	 */
	public static PathKey getPathKeyStatic(Path path) {
		path = requireLocalAbsolutePath(path);

		return getPathKeyStaticImpl(SakerPath.valueOf(path));
	}

	/**
	 * Validation method for ensuring that the argument path is absolute and associated with the
	 * {@linkplain FileSystems#getDefault() local file system}.
	 * 
	 * @param path
	 *            The path to validate-
	 * @return The parameter path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute, or not associated with the default file system.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 */
	public static Path requireLocalAbsolutePath(Path path) throws InvalidPathFormatException, NullPointerException {
		SakerPathFiles.requireAbsolutePath(path);
		if (path.getFileSystem() != localFileSystem) {
			throw new InvalidPathFormatException("Path is not associated with the default filesystem. (" + path + ")");
		}
		//need to normalize, as the following fails on ubuntu:
		//    Files.readAttributes(Paths.get(".").toAbsolutePath().normalize().resolve("x").resolve(".."), BasicFileAttributes.class);
		// however, succeeds on windows.
		return path.normalize();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getProviderKey() + "]";
	}

	private static PathKey getPathKeyStaticImpl(SakerPath mpath) {
		return new SimplePathKey(mpath, PROVIDER_KEY);
	}

	//doc: true if there was any file or directory deleted
	private static boolean deleteRecursivelyImpl(Path directory) throws IOException {
		//do not follow links
		RecursiveDeletorFileVisitor visitor = new RecursiveDeletorFileVisitor(directory);
		Files.walkFileTree(directory, DONT_FOLLOW_LINKS_FILEVISITOPTIONS, Integer.MAX_VALUE, visitor);
		//no need to delete the directory itself, as it is visited as the first directory for the walk
		return visitor.deleteCount > 0;
	}

	private static void clearDirectoryImpl(Path directory) throws IOException {
		//no need to use file walker
		DirectoryStream<Path> entries;
		try {
			entries = localFileSystemProvider.newDirectoryStream(directory, AcceptAllFilter.INSTANCE);
		} catch (NoSuchFileException e) {
			// the directory doesn't exist, the stream is not created, we can return early
			return;
		}
		try (DirectoryStream<Path> theentries = entries) {
			for (Path p : theentries) {
				deleteRecursivelyImpl(p);
			}
		} catch (NotDirectoryException e) {
		}
	}

	//doc: true if there was any directory created
	private static int createDirectoriesImpl(Path path, int opflags) throws FileAlreadyExistsException, IOException {
		//find the first existing parent directory and create new ones from there
		//dont need to use Files.createDirectories as that isnt better at all

		try {
			switch (readFileEntryImpl(path, EMPTY_LINK_OPTIONS).getType()) {
				case FileEntry.TYPE_DIRECTORY: {
					//already a directory
					return RESULT_FLAG_DIRECTORY_CREATED;
				}
				case FileEntry.TYPE_FILE: {
					if (((opflags
							& OPERATION_FLAG_DELETE_INTERMEDIATE_FILES) == OPERATION_FLAG_DELETE_INTERMEDIATE_FILES)) {
						//delete the existing file
						int result = 0;
						if (localFileSystemProvider.deleteIfExists(path)) {
							result |= RESULT_FLAG_FILES_DELETED;
						}
						localFileSystemProvider.createDirectory(path);
						return result | RESULT_FLAG_DIRECTORY_CREATED;
					}
					break;
				}
				default: {
					//something else. don't overwrite
					throw new FileAlreadyExistsException(path.toString());
				}
			}
		} catch (IOException e) {
		}
		Path parent = path.getParent();
		int result = 0;
		if (parent != null) {
			result |= createDirectoriesImpl(parent, opflags);
		}
		try {
			localFileSystemProvider.createDirectory(path);
			return result | RESULT_FLAG_DIRECTORY_CREATED;
		} catch (FileAlreadyExistsException e) {
			//might've been created concurrently
			FileEntry entry;
			try {
				entry = readFileEntryImpl(path, EMPTY_LINK_OPTIONS);
			} catch (IOException e2) {
				//concurrency errors?
				e.addSuppressed(e2);
				throw e;
			}
			switch (entry.getType()) {
				case FileEntry.TYPE_DIRECTORY: {
					//concurrently recreated
					return result;
				}
				case FileEntry.TYPE_FILE: {
					//we should've discovered this file presence at the start of this method
					//it was concurrently created
					//we should fail instead of trying to delete it
					throw e;
				}
				default: {
					//can't determine file type
					throw e;
				}
			}
			//unreachable
		}
	}

	private FileEventListener.ListenerToken addFileEventListenerImpl(Path path, FileEventListener listener)
			throws IOException {
		WatcherThread wthread = getWatcherThread();
		if (wthread == null) {
			throw new IOException("No watcher available.");
		}
		return wthread.addFileEventListener(this, path, listener);
	}

	private static SakerFileLock createLockFileImpl(Path path) throws IOException {
		try {
			FileChannel channel = localFileSystemProvider.newFileChannel(path, LOCKFILE_OPENOPTIONS);
			return new LocalFileSakerLock(channel);
		} catch (UnsupportedOperationException e) {
			throw new IOException("File locking is not supported at: " + path, e);
		}
	}

	private static int deleteIfNotFileTypeImpl(Path path, int filetype) throws IOException {
		try {
			int existingtype = readFileEntryImpl(path, EMPTY_LINK_OPTIONS).getType();
			if (existingtype == filetype) {
				return filetype;
			}
		} catch (IOException e) {
			//not existing, we are okay
			return FileEntry.TYPE_NULL;
		}
		deleteRecursivelyImpl(path);
		return FileEntry.TYPE_NULL;
	}

	private NavigableSet<String> deleteChildrenIfNotInImpl(Path path, Set<String> childfilenames) throws IOException {
		NavigableSet<String> deleted = new TreeSet<>();
		//don't follow links
		Files.walkFileTree(path, DONT_FOLLOW_LINKS_FILEVISITOPTIONS, 1,
				new DeleteChildrenIfNotInFileVisitor(path, childfilenames, deleted));
		return deleted;
	}

	private static ReferencedInputStream openInputImpl(Path path, OpenOption[] openoptions) throws IOException {
		return openInputStreamImpl(path, openoptions);
	}

	private static ReferencedOutputStream openOutputImpl(Path path, OpenOption[] openoptions) throws IOException {
		return openOutputStreamImpl(path, openoptions);
	}

	private static ReferencedInputStream openInputStreamImpl(Path path, OpenOption[] openoptions) throws IOException {
		//XXX should we buffer this by default?
		return new ReferencedInputStream(localFileSystemProvider.newInputStream(path, openoptions));
	}

	private static ReferencedOutputStream openOutputStreamImpl(Path path, OpenOption[] openoptions) throws IOException {
		//TODO should we buffer to another file, then move it at closing?
		return new ReferencedOutputStream(localFileSystemProvider.newOutputStream(path, openoptions));
	}

	private static long writeToStreamImpl(Path path, OutputStream os, OpenOption[] openoptions) throws IOException {
		try (InputStream is = openInputStreamImpl(path, openoptions)) {
			return StreamUtils.copyStream(is, os);
		}
	}

	private static long writeToFileImpl(InputStream is, Path path, OpenOption[] openoptions) throws IOException {
		//do not use Files.copy as it deletes and later opens the file
		//use newoutputstream as we overwrite the contents
		//dont use openOutputStreamImpl, as we don't need to garbage track the stream
		try (OutputStream os = localFileSystemProvider.newOutputStream(path, openoptions)) {
			return StreamUtils.copyStream(is, os);
		}
	}

	private static long writeToImpl(Path path, ByteSink out, OpenOption[] openoptions) throws IOException {
		//dont use openInputStreamImpl, as we don't need to garbage track the stream
		try (ByteSource is = ByteSource.valueOf(localFileSystemProvider.newInputStream(path, openoptions))) {
			return out.readFrom(is);
		}
	}

	private static ByteArrayRegion getAllBytesImpl(Path path, OpenOption[] openoptions) throws IOException {
		//dont use openInputStreamImpl, as we don't need to garbage track the stream
		try (InputStream is = localFileSystemProvider.newInputStream(path, openoptions);
				UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			baos.readFrom(is);
			return baos.toByteArrayRegion();
		}
	}

	private static NavigableMap<String, ? extends FileEntry> getDirectoryEntriesByNameImpl(Path path)
			throws IOException, AssertionError {
		return collectDirectoryEntries(path);
	}

	private static NavigableMap<String, FileEntry> collectDirectoryEntries(Path path) throws IOException {
		NavigableMap<String, FileEntry> filenames = new TreeMap<>();
		collectDirectoryEntriesImpl(path, filenames);
		return filenames;
	}

	private static void collectDirectoryEntriesImpl(Path path, NavigableMap<String, FileEntry> filenames)
			throws IOException {
		Files.walkFileTree(path, FOLLOW_LINKS_FILEVISITOPTIONS, 1, new DirectoryEntryCollectorFileVisitor(filenames));
	}

	private static enum AcceptAllFilter implements DirectoryStream.Filter<Path> {
		INSTANCE;

		@Override
		public boolean accept(Path entry) throws IOException {
			return true;
		}
	}

	private static NavigableSet<String> getDirectoryEntryNamesImpl(Path path) throws IOException {
		NavigableSet<String> result = new TreeSet<>();
		try (DirectoryStream<Path> stream = localFileSystemProvider.newDirectoryStream(path,
				AcceptAllFilter.INSTANCE)) {
			for (Path file : stream) {
				result.add(file.getFileName().toString());
			}
		}
		return result;
	}

	private static NavigableSet<String> getSubDirectoryNamesImpl(Path path) throws IOException {
		NavigableSet<String> result = new TreeSet<>();
		//don't follow links
		Files.walkFileTree(path, DONT_FOLLOW_LINKS_FILEVISITOPTIONS, 1, new SubDirectoryNamesFileVisitor(result, path));
		return result;
	}

	private static boolean isChangedImpl(Path path, long size, long modificationmillis, LinkOption[] linkoptions) {
		return isChangedReadAttributesImpl(path, size, modificationmillis, linkoptions);
	}

	private static boolean isChangedReadAttributesImpl(Path path, long size, long modificationmillis,
			LinkOption[] linkoptions) {
		try {
			FileEntry entry = readFileEntryImpl(path, linkoptions);
			return entry.size() != size || entry.getLastModifiedMillis() != modificationmillis;
		} catch (IOException e) {
		}
		return true;
	}

	private static boolean isDirectory(Path file, LinkOption[] linkoptions) {
		try {
			return localFileSystemProvider.readAttributes(file, BasicFileAttributes.class, linkoptions).isDirectory();
		} catch (IOException e) {
			return false;
		}
	}

	private static FileEntry readFileEntryImpl(Path file, LinkOption[] linkoptions) throws IOException {
		BasicFileAttributes readattrs = localFileSystemProvider.readAttributes(file, BasicFileAttributes.class,
				linkoptions);
		return new FileEntry(readattrs);
	}

	private static void addRecursiveDirectoryEntriesImpl(Path path, SakerPath resolve,
			NavigableMap<SakerPath, ? super FileEntry> result) throws IOException {
		Files.walkFileTree(path, FOLLOW_LINKS_FILEVISITOPTIONS, Integer.MAX_VALUE,
				new RecursiveDirectoryEntryCollectorFileVisitor(resolve, result));
	}

	private static NavigableMap<SakerPath, ? extends FileEntry> getRecursiveDirectoryEntriesImpl(Path path)
			throws IOException {
		NavigableMap<SakerPath, FileEntry> result = new TreeMap<>();
		addRecursiveDirectoryEntriesImpl(path, SakerPath.EMPTY, result);
		return result;
	}

	private static NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursivelyImpl(Path path)
			throws IOException {
		return getRecursiveDirectoryEntriesImpl(path);
	}
	//XXX we should replace Files.walkFileTree calls to direct localFileSystemProvider calls, or maybe JNI implementation as BasicFileAttributesHolder is internal class
	//    and is slower when a security manager is installed

}