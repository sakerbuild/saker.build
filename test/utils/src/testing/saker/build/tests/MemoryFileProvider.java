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
package testing.saker.build.tests;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileEventListener;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileLock;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

public class MemoryFileProvider implements SakerFileProvider {
	//XXX should refactor this class to a tree like concurrent representation

	private static class MemoryFile {
		private final String name;
		private volatile Supplier<ByteArrayRegion> bytes = Functionals.valSupplier(ByteArrayRegion.EMPTY);
		private volatile FileEntry attributes;
		private volatile SakerFileLock locker = null;

		public MemoryFile(String name) {
			this.name = name;
			this.attributes = new FileEntry(FileEntry.TYPE_FILE, 0, currentFileTime());
		}

		public MemoryFile(String name, FileEntry attributes) {
			this.name = name;
			this.attributes = attributes;
		}

		public UnsyncByteArrayInputStream openInputStream() {
			if (bytes == null) {
				return new UnsyncByteArrayInputStream(ObjectUtils.EMPTY_BYTE_ARRAY);
			}
			return new UnsyncByteArrayInputStream(bytes.get());
		}

		public UnsyncByteArrayOutputStream openOutputStream() throws IOException {
			FileEntry startattributes = this.attributes;
			if (startattributes != null && startattributes.isDirectory()) {
				throw new IOException("Cannot open output for directory.");
			}
			setAttributes(new FileEntry(FileEntry.TYPE_FILE, 0, currentFileTime()));
			return new UnsyncByteArrayOutputStream() {
				@Override
				public void close() {
					MemoryFile.this.bytes = this::toByteArrayRegion;
					MemoryFile.this.setAttributes(new FileEntry(FileEntry.TYPE_FILE, this.size(), currentFileTime()));
					super.close();
				}
			};
		}

		private void setAttributes(FileEntry attributes) {
			this.attributes = attributes;
		}

		public FileEntry getAttributes() {
			return attributes;
		}

		public SakerFileLock createLock() {
			return new SakerFileLock() {
				private boolean closed = false;

				private void checkClosed() throws IOException {
					if (closed) {
						throw new IOException("closed");
					}
				}

				@Override
				public void close() throws IOException {
					synchronized (MemoryFile.this) {
						closed = true;
						if (locker == this) {
							locker = null;
							MemoryFile.this.notifyAll();
						}
					}
				}

				@Override
				public void lock() throws IOException {
					synchronized (MemoryFile.this) {
						while (locker != this) {
							checkClosed();
							try {
								MemoryFile.this.wait();
							} catch (InterruptedException e) {
								throw new InterruptedIOException(e.toString());
							}
						}
						locker = this;
					}
				}

				@Override
				public boolean tryLock() throws IOException {
					synchronized (MemoryFile.this) {
						checkClosed();
						if (locker != null) {
							return false;
						}
						locker = this;
						return true;
					}
				}

				@Override
				public void release() throws IOException {
					synchronized (MemoryFile.this) {
						checkClosed();
						if (locker != this) {
							throw new IOException("Not locked.");
						}
						MemoryFile.this.locker = this;
						MemoryFile.this.notify();
					}
				}
			};
		}

		public synchronized boolean isLocked() {
			return locker != null;
		}
	}

	private static final AtomicLong currentTimeDrifter = new AtomicLong();

	private ConcurrentNavigableMap<SakerPath, Collection<WeakReference<FileEventListener>>> directoryListeners = new ConcurrentSkipListMap<>();

	private ConcurrentNavigableMap<SakerPath, MemoryFile> files = new ConcurrentSkipListMap<>();
	private NavigableSet<String> roots = new TreeSet<>();

	private RootFileProviderKey providerKey;

	public MemoryFileProvider(Set<String> roots, UUID provideruuid) {
		providerKey = new MemoryFileProviderKey(provideruuid);
		FileTime rootmodtime = currentFileTime();
		for (String r : roots) {
			SakerPath rpath = SakerPath.valueOf(r);
			this.roots.add(rpath.getRoot());
			this.files.put(rpath, new MemoryFile(null, new FileEntry(FileEntry.TYPE_DIRECTORY, 0, rootmodtime)));
		}
		this.roots = ImmutableUtils.unmodifiableNavigableSet(this.roots);
	}

	public void addDirectoryTo(SakerPath path, Path directory) throws IOException {
		try {
			BasicFileAttributes attrs = Files.readAttributes(directory, BasicFileAttributes.class);
			if (!attrs.isDirectory()) {
				throw new NotDirectoryException(directory.toString());
			}
		} catch (NoSuchFileException e) {
			return;
		}
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				addFile(dir, attrs);
				return FileVisitResult.CONTINUE;
			}

			private void addFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path relative = directory.relativize(file);
				if (relative.getNameCount() == 0 || relative.getFileName().toString().isEmpty()) {
					return;
				}
				SakerPath targetpath = path.resolve(SakerPath.valueOf(relative));
				String filename = targetpath.getFileName();
				MemoryFile f = new MemoryFile(filename, new FileEntry(attrs));
				MemoryFile prev = files.put(targetpath, f);
				if (prev != null) {
					throw new IOException("Duplicate file at: " + targetpath);
				}
				if (!attrs.isDirectory()) {
					f.bytes = LazySupplier.of(() -> {
						try {
							return ByteArrayRegion.wrap(Files.readAllBytes(file));
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
				}
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				addFile(file, attrs);

				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public Set<String> getRoots() {
		return roots;
	}

	private void checkRoot(SakerPath path) {
		SakerPathFiles.requireAbsolutePath(path);
		if (!roots.contains(path.getRoot())) {
			throw new IllegalArgumentException(
					"No root found for: " + path + " with " + path.getRoot() + " available: " + roots);
		}
	}

	private static <T> boolean iterateDirectoryIterator(NavigableMap<SakerPath, T> map, SakerPath directorypath,
			BiFunction<Iterator<Entry<SakerPath, T>>, T, Boolean> consumer) {
		int pathnamecount = directorypath.getNameCount();
		NavigableMap<SakerPath, T> tmap = map.tailMap(directorypath, false);
		for (Iterator<Entry<SakerPath, T>> it = tmap.entrySet().iterator(); it.hasNext();) {
			Entry<SakerPath, T> entry = it.next();
			SakerPath child = entry.getKey();
			if (!child.startsWith(directorypath)) {
				break;
			}
			if (child.getNameCount() != pathnamecount + 1) {
				continue;
			}

			if (!consumer.apply(it, entry.getValue())) {
				return false;
			}
		}
		return true;
	}

	private static <T> boolean iterateDirectory(NavigableMap<SakerPath, T> map, SakerPath directorypath,
			Function<T, Boolean> consumer) {
		return iterateDirectoryIterator(map, directorypath, (it, e) -> {
			return consumer.apply(e);
		});
	}

	@Override
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(SakerPath path) throws IOException {
		checkRoot(path);
		MemoryFile fdir = files.get(path);
		if (fdir == null) {
			throw new IOException("File not found: " + path);
		}
		if (!fdir.attributes.isDirectory()) {
			throw new IOException("Not a directory: " + path);
		}
		TreeMap<String, FileEntry> result = new TreeMap<>();

		iterateDirectory(files, path, f -> {
			result.put(f.name, f.attributes);
			return true;
		});
		return result;
	}

	public SortedSet<String> getDirectoryEntryNamesRecursive(SakerPath path) {
		checkRoot(path);
		SortedSet<String> result = new TreeSet<>();
		for (Iterator<Entry<SakerPath, MemoryFile>> it = files.tailMap(path, false).entrySet().iterator(); it
				.hasNext();) {
			Entry<SakerPath, MemoryFile> entry = it.next();
			if (!entry.getKey().startsWith(path)) {
				break;
			}
			result.add(path.relativize(entry.getKey()).toString());
		}
		return result;
	}

	@Override
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(SakerPath path)
			throws IOException {
		checkRoot(path);
		NavigableMap<SakerPath, FileEntry> result = new TreeMap<>();
		for (Iterator<Entry<SakerPath, MemoryFile>> it = files.tailMap(path, false).entrySet().iterator(); it
				.hasNext();) {
			Entry<SakerPath, MemoryFile> entry = it.next();
			if (!entry.getKey().startsWith(path)) {
				break;
			}
			SakerPath relstr = path.relativize(entry.getKey());
			MemoryFile f = entry.getValue();
			result.put(relstr, f.attributes);
		}
		return result;
	}

	public SortedMap<String, ByteArrayRegion> getDirectoryContentsRecursive(SakerPath path) {
		checkRoot(path);
		SortedMap<String, ByteArrayRegion> result = new TreeMap<>();
		for (Iterator<Entry<SakerPath, MemoryFile>> it = files.tailMap(path, false).entrySet().iterator(); it
				.hasNext();) {
			Entry<SakerPath, MemoryFile> entry = it.next();
			if (!entry.getKey().startsWith(path)) {
				break;
			}
			String relstr = path.relativize(entry.getKey()).toString();
			MemoryFile f = entry.getValue();
			result.put(relstr, f.bytes.get());
		}
		return result;
	}

	@Override
	public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException, NoSuchFileException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f == null) {
			throw fileNotFound(path);
		}
		return f.openInputStream();
	}

	public long writeToStream(SakerPath path, OutputStream os) throws IOException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f == null) {
			throw fileNotFound(path);
		}
		ByteArrayRegion bytes = f.bytes.get();
		bytes.writeTo(os);
		return bytes.getLength();
	}

	@Override
	public void moveFile(SakerPath source, SakerPath target, CopyOption... copyoptions) throws IOException {
		checkRoot(source);
		checkRoot(target);
		MemoryFile f = files.get(source);
		if (f == null) {
			throw fileNotFound(source);
		}
		if (f.getAttributes().isDirectory()) {
			// TODO handle moving children if directory
			throw new IOException("Directory move not yet supported.");
		}
		try (UnsyncByteArrayInputStream is = f.openInputStream()) {
			writeToFile(is, target);
		}
		deleteRecursively(source);
	}

	private NoSuchFileException fileNotFound(SakerPath path) {
		Entry<SakerPath, MemoryFile> parent = SakerPathFiles.getPathOrParentEntry(files, path);
		return new NoSuchFileException(
				"File not found: " + path + (parent == null ? "" : " first parent: " + parent.getKey()));
	}

	@Override
	public ByteArrayRegion getAllBytes(SakerPath path, OpenOption... openoptions) throws IOException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f == null) {
			throw fileNotFound(path);
		}
		return f.bytes == null ? ByteArrayRegion.EMPTY : f.bytes.get();
	}

	@Override
	public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
		checkRoot(path);
		if (path.getNameCount() == 0) {
			throw new IOException("Failed open output stream for: " + path);
		}
		SakerPath parentpath = path.getParent();
		String filename = path.getFileName();

		MemoryFile f = files.get(path);
		if (f != null) {
			UnsyncByteArrayOutputStream result = f.openOutputStream();
			callListeners(parentpath, filename, FileEventListener::changed);
			return result;
		}
		MemoryFile parent = files.get(parentpath);
		if (parent == null) {
			throw new IOException("Parent directory doesn't exist for: " + path);
		}
		if (!parent.attributes.isDirectory()) {
			throw new IOException("Parent is not a directory for: " + path);
		}
		f = new MemoryFile(filename);
		MemoryFile prev = files.putIfAbsent(path, f);
		if (prev != null) {
			UnsyncByteArrayOutputStream result = prev.openOutputStream();
			callListeners(parentpath, filename, FileEventListener::changed);
			return result;
		}
		UnsyncByteArrayOutputStream result = f.openOutputStream();
		callListeners(parentpath, filename, FileEventListener::changed);
		return result;
	}

	@Override
	public int ensureWriteRequest(SakerPath path, int filetype, int opflag)
			throws IllegalArgumentException, IOException {
		checkRoot(path);
		switch (filetype) {
			case FileEntry.TYPE_FILE: {
				MemoryFile f = files.get(path);
				if (f != null) {
					if (f.attributes.getType() == FileEntry.TYPE_FILE) {
						//already the same type
						return RESULT_NO_CHANGES;
					}
					if (((opflag
							& OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE) == OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE)) {
						if (deleteImpl(path)) {
							return RESULT_FLAG_FILES_DELETED;
						}
						return RESULT_NO_CHANGES;
					}
					if (clearDirectoryRecursivelyImpl(path)) {
						return RESULT_FLAG_FILES_DELETED;
					}
					return RESULT_NO_CHANGES;
				}
				if (createDirectoriesImpl(path.getParent())) {
					return RESULT_FLAG_DIRECTORY_CREATED;
				}
				return RESULT_NO_CHANGES;
			}
			case FileEntry.TYPE_DIRECTORY: {
				while (true) {
					MemoryFile f = files.get(path);
					if (f != null) {
						if (f.attributes.getType() == FileEntry.TYPE_DIRECTORY) {
							//already the same type
							return RESULT_NO_CHANGES;
						}
						if (!files.remove(path, f)) {
							//try again, due to concurrent modification
							continue;
						}
						callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
						return RESULT_FLAG_FILES_DELETED
								| (createDirectoriesImpl(path) ? RESULT_FLAG_DIRECTORY_CREATED : 0);
					}
					if (createDirectoriesImpl(path)) {
						return RESULT_FLAG_DIRECTORY_CREATED;
					}
					return RESULT_NO_CHANGES;
				}
			}
			default: {
				throw new IllegalArgumentException("Invalid file type: " + filetype);
			}
		}
	}

	@Override
	public FileEntry getFileAttributes(SakerPath path, LinkOption... linkoptions)
			throws IOException, NoSuchFileException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f == null) {
			throw fileNotFound(path);
		}
		return f.getAttributes();
	}

	@Override
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f == null) {
			throw fileNotFound(path);
		}
		f.attributes = new FileEntry(f.attributes.getType(), f.attributes.getSize(), FileTime.fromMillis(millis));
		callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
	}

	@Override
	public void createDirectories(SakerPath path) throws IOException {
		createDirectoriesImpl(path);
	}

	private boolean createDirectoriesImpl(SakerPath path) throws IOException {
		checkRoot(path);
		int nc = path.getNameCount();
		if (nc == 0) {
			return false;
		}
		for (int i = 1; i <= nc; i++) {
			SakerPath base = path.subPath(path.getRoot(), 0, i);
			String fname = base.getFileName();
			MemoryFile got = files.get(base);
			if (got == null) {
				MemoryFile prev = files.putIfAbsent(base,
						new MemoryFile(fname, new FileEntry(FileEntry.TYPE_DIRECTORY, 0, currentFileTime())));
				if (prev != null) {
					if (!prev.attributes.isDirectory()) {
						throw new FileAlreadyExistsException("Failed to create directory at: " + base);
					}
					//else is a directory
					if (i == nc) {
						return false;
					}
				}
				//else successfully put it
				callListeners(base.getParent(), fname, FileEventListener::changed);
				if (i == nc) {
					return true;
				}
			} else {
				if (!got.attributes.isDirectory()) {
					throw new FileAlreadyExistsException(
							"Failed to create directory at: " + base + " - " + got.attributes.getType());
				}
				if (i == nc) {
					return false;
				}
			}
		}
		throw new AssertionError("unreachable");
	}

	private static FileTime currentFileTime() {
		return FileTime.fromMillis(System.currentTimeMillis() + currentTimeDrifter.getAndIncrement() * 1000);
	}

	@Override
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException {
		deleteImpl(path);
	}

	private boolean deleteImpl(SakerPath path) throws DirectoryNotEmptyException, AssertionError {
		checkRoot(path);
		while (true) {
			MemoryFile f = files.get(path);
			if (f == null) {
				return false;
			}
			if (f.attributes.isRegularFile()) {
				if (!files.remove(path, f)) {
					continue;
				}
				callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
				return true;
			}
			if (f.attributes.isDirectory()) {
				//check if it has any children
				if (SakerPathFiles.getPathSubMapDirectoryChildren(files, path, false).isEmpty()) {
					//XXX others could put files in the directory concurrently
					if (files.remove(path, f)) {
						continue;
					}
					callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
					return true;
				}
				throw new DirectoryNotEmptyException(path.toString());
			}
			throw new AssertionError(f);
		}
	}

	@Override
	public void deleteRecursively(SakerPath path) throws IOException {
		deleteRecursivelyImpl(path);
	}

	private boolean deleteRecursivelyImpl(SakerPath path) throws IOException {
		boolean deletedanything = false;
		checkRoot(path);
		while (true) {
			MemoryFile f = files.get(path);
			if (f == null) {
				break;
			}
			if (f.isLocked()) {
				throw new IOException("File is locked: path");
			}
			boolean isdir = f.attributes.isDirectory();
			if (isdir) {
				//deleting a directory
				clearDirectoryRecursively(path);
			}
			boolean removed = files.remove(path, f);
			if (!removed) {
				continue;
			}
			callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
			if (isdir) {
				listenersAbandoned(path);
			}
			break;
		}
		return deletedanything;
	}

	@Override
	public void clearDirectoryRecursively(SakerPath path) throws IOException {
		clearDirectoryRecursivelyImpl(path);
	}

	private boolean clearDirectoryRecursivelyImpl(SakerPath path) throws IOException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f == null) {
			return false;
		}
		if (!f.attributes.isDirectory()) {
			throw new IOException("Not a directory: " + path);
		}
		boolean deletedanything = false;
		for (String child : getDirectoryEntryNames(path)) {
			deletedanything |= deleteRecursivelyImpl(path.resolve(child));
		}
		return deletedanything;
	}

	public void touch(SakerPath path) throws IOException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f != null) {
			if (f.isLocked()) {
				throw new IOException("File is locked: " + path);
			}
			f.attributes = new FileEntry(f.attributes.getType(), f.attributes.getSize(), currentFileTime());
			callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
		} else {
			throw fileNotFound(path);
		}
	}

	public void putFile(SakerPath path, ByteArrayRegion bytes) throws IOException {
		createDirectories(path.getParent());
		setFileBytes(path, bytes);
	}

	public void putFile(SakerPath path, String content) throws IOException {
		putFile(path, content.getBytes(StandardCharsets.UTF_8));
	}

	public void putFile(SakerPath path, byte[] bytes) throws IOException {
		putFile(path, ByteArrayRegion.wrap(bytes));
	}

	public void setFileBytes(SakerPath path, byte[] data) throws IOException {
		setFileBytes(path, ByteArrayRegion.wrap(data));
	}

	public NavigableMap<SakerPath, MemoryFile> getFiles() {
		return files;
	}

	public void triggerListenerAbandon(SakerPath path) {
		listenersAbandoned(path);
	}

	public void triggerListenerEventsMissed(SakerPath path) {
		listenerEventsMissed(path);
	}

	private void listenerEventsMissed(SakerPath path) {
		Collection<WeakReference<FileEventListener>> coll = directoryListeners.get(path);
		if (coll == null) {
			return;
		}
		for (Iterator<WeakReference<FileEventListener>> it = coll.iterator(); it.hasNext();) {
			WeakReference<FileEventListener> listenerref = it.next();
			FileEventListener listener = listenerref.get();
			if (listener != null) {
				listener.eventsMissed();
			}
		}
	}

	private void listenersAbandoned(SakerPath directory) {
		Collection<WeakReference<FileEventListener>> coll = directoryListeners.get(directory);
		if (coll == null) {
			return;
		}
		for (Iterator<WeakReference<FileEventListener>> it = coll.iterator(); it.hasNext();) {
			WeakReference<FileEventListener> listenerref = it.next();
			FileEventListener listener = listenerref.get();
			if (listener != null) {
				listener.listenerAbandoned();
			}
			it.remove();
		}
	}

	private void callListeners(SakerPath directory, String name, BiConsumer<FileEventListener, String> caller) {
		Collection<WeakReference<FileEventListener>> coll = directoryListeners.get(directory);
		if (coll == null) {
			return;
		}
		for (Iterator<WeakReference<FileEventListener>> it = coll.iterator(); it.hasNext();) {
			WeakReference<FileEventListener> listenerref = it.next();
			FileEventListener listener = listenerref.get();
			if (listener == null) {
				it.remove();
			} else {
				caller.accept(listener, name);
			}
		}
	}

	private class MemoryListenerToken implements FileEventListener.ListenerToken {
		protected Collection<WeakReference<FileEventListener>> ownerCollection;
		//unused, only to keep a strong reference to the listener
		@SuppressWarnings("unused")
		protected FileEventListener strongRef;
		protected WeakReference<FileEventListener> weakRef;

		public MemoryListenerToken(Collection<WeakReference<FileEventListener>> ownerCollection,
				FileEventListener strongRef) {
			this.ownerCollection = ownerCollection;
			this.strongRef = strongRef;
			this.weakRef = new WeakReference<>(strongRef);
		}

		@Override
		public void removeListener() {
			WeakReference<FileEventListener> weakref = weakRef;
			if (weakref != null) {
				ownerCollection.remove(weakref);
				strongRef = null;
			}
		}
	}

	@Override
	public FileEventListener.ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener)
			throws IOException {
		checkRoot(directory);
		MemoryFile f = files.get(directory);
		if (f == null) {
			throw fileNotFound(directory);
		}
		if (!f.attributes.isDirectory()) {
			throw new IOException("Not directory.");
		}
		Collection<WeakReference<FileEventListener>> coll = directoryListeners.get(directory);
		if (coll == null) {
			coll = ConcurrentHashMap.newKeySet();
			Collection<WeakReference<FileEventListener>> prev = directoryListeners.putIfAbsent(directory, coll);
			if (prev != null) {
				coll = prev;
			}
		}
		MemoryListenerToken token = new MemoryListenerToken(coll, listener);
		coll.add(token.weakRef);
		return token;
	}

	@Override
	public SakerFileLock createLockFile(SakerPath path) throws IOException {
		checkRoot(path);
		MemoryFile f = files.get(path);
		if (f != null) {
			if (f.getAttributes().isDirectory()) {
				throw new IOException("Cannot create lock for directory: " + path);
			}
			return f.createLock();
		}
		SakerPath parent = path.getParent();
		MemoryFile dir = files.get(parent);
		if (dir == null) {
			throw new IOException("Cannot create lock file, parent directory doesnt exist: " + path);
		}
		if (!dir.getAttributes().isDirectory()) {
			throw new IOException("Cannot create lock file, parent is not directory: " + path);
		}
		f = new MemoryFile(path.getFileName());
		MemoryFile prev = files.putIfAbsent(path, f);
		if (prev != null) {
			if (!prev.getAttributes().isRegularFile()) {
				throw new IOException("Cannot create lock file, not regular file: " + path);
			}
			return prev.createLock();
		}
		callListeners(path.getParent(), path.getFileName(), FileEventListener::changed);
		return f.createLock();
	}

	@RMIWriter(SerializeRMIObjectWriteHandler.class)
	private static class MemoryFileProviderKey implements RootFileProviderKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private UUID uuid;

		public MemoryFileProviderKey() {
		}

		public MemoryFileProviderKey(UUID uuid) {
			this.uuid = uuid;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + uuid.toString() + "]";
		}

		@Override
		public UUID getUUID() {
			return uuid;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
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
			MemoryFileProviderKey other = (MemoryFileProviderKey) obj;
			if (uuid == null) {
				if (other.uuid != null)
					return false;
			} else if (!uuid.equals(other.uuid))
				return false;
			return true;
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

	@Override
	public RootFileProviderKey getProviderKey() {
		return providerKey;
	}

}
