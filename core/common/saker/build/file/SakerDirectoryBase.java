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
package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;

public abstract class SakerDirectoryBase extends SakerFileBase implements SakerDirectory {
	//XXX are these unused?
	public static final Predicate<SakerFileBase> PREDICATE_NOT_DIRECTORY = f -> !(f instanceof SakerDirectory);
	public static final Predicate<SakerFileBase> PREDICATE_DIRECTORY = f -> f instanceof SakerDirectory;

	protected static final AtomicIntegerFieldUpdater<SakerDirectoryBase> AIFU_populatedState = AtomicIntegerFieldUpdater
			.newUpdater(SakerDirectoryBase.class, "populatedState");
	protected static final int POPULATED_STATE_UNPOPULATED = 0;
	protected static final int POPULATED_STATE_PARTIALLY_POPULATED = 1;
	protected static final int POPULATED_STATE_POPULATED = 2;

	protected ConcurrentNavigableMap<String, SakerFileBase> trackedFiles = new ConcurrentSkipListMap<>();

	protected volatile int populatedState = POPULATED_STATE_UNPOPULATED;

	/* default */ SakerDirectoryBase(String name) {
		super(name);
	}

	/* default */ SakerDirectoryBase(String name, Void placeholder) {
		super(name, placeholder);
	}

	@Override
	public SakerFile add(SakerFile file) {
		Objects.requireNonNull(file, "file");
		SakerFileBase basef = (SakerFileBase) file;
		if (!internal_casParent(basef, null, this)) {
			if (internal_getParent(basef) == MarkerSakerDirectory.REMOVED_FROM_PARENT) {
				throw new IllegalStateException("File was already removed from its parent: " + file);
			}
			throw new IllegalStateException("File already has a parent: " + file);
		}

		String filename = file.getName();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		SakerFileBase prev = trackedfiles.put(filename, basef);
		if (prev != null) {
			if (prev != MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				internal_casParent(prev, this, MarkerSakerDirectory.REMOVED_FROM_PARENT);
			}
		}
		if (internal_getParent(basef) != this) {
			//the parent of the file was concurrently changed
			//this can happen when the file is being added, and an other thread calls remove() on it.
			//this race condition happens because the addition and the CAS of the parent is not an atomic operation
			//therefore we need to check if the parent of the file still points to this object, 
			//and if not, then remove the file from this directory
			trackedfiles.remove(filename, basef);
		}
		return prev;
	}

	@Override
	public SakerFile addIfAbsent(SakerFile file) throws NullPointerException, IllegalStateException {
		Objects.requireNonNull(file, "file");
		SakerFileBase basef = (SakerFileBase) file;
		if (!internal_casParent(basef, null, this)) {
			if (internal_getParent(basef) == MarkerSakerDirectory.REMOVED_FROM_PARENT) {
				throw new IllegalStateException("File was already removed from its parent: " + file);
			}
			throw new IllegalStateException("File already has a parent: " + file);
		}

		String filename = file.getName();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		SakerFileBase prev = putIfAbsentImpl(trackedfiles, basef);
		if (prev != null) {
			//a file is already present, do not add the file
			//reset the parent back to null
			internal_casParent(basef, this, null);
			return prev;
		}
		if (internal_getParent(basef) != this) {
			//see add(SakerFile) why do this
			trackedfiles.remove(filename, basef);
		}
		return null;
	}

	@Override
	public SakerDirectory addOverwriteIfNotDirectory(SakerFile file)
			throws NullPointerException, IllegalStateException {
		Objects.requireNonNull(file, "file");
		SakerFileBase basef = (SakerFileBase) file;
		if (!internal_casParent(basef, null, this)) {
			if (internal_getParent(basef) == MarkerSakerDirectory.REMOVED_FROM_PARENT) {
				throw new IllegalStateException("File was already removed from its parent: " + file);
			}
			throw new IllegalStateException("File already has a parent: " + file);
		}

		String filename = file.getName();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		while (true) {
			SakerFileBase prev = putIfAbsentImpl(trackedfiles, basef);
			if (prev == null) {
				//successfully added it
				if (internal_getParent(basef) != this) {
					//see add(SakerFile) why do this
					trackedfiles.remove(filename, basef);
				}
				return null;
			}
			//there is a previous file
			if (prev instanceof SakerDirectory) {
				internal_casParent(basef, this, null);
				return (SakerDirectory) prev;
			}
			//the file can be overwritten
			if (trackedfiles.replace(filename, prev, basef)) {
				//successful replacement, return
				if (internal_getParent(basef) != this) {
					//see add(SakerFile) why do this
					trackedfiles.remove(filename, basef);
				}
				return null;
			}
			//try again, due to concurrent modifications
		}
	}

	@Override
	public final SakerFile get(String name) {
		SakerPathFiles.requireValidFileName(name);
		return getImpl(name);
	}

	@Override
	public NavigableMap<String, ? extends SakerFile> getChildren() {
		ensurePopulated();
		//a concurrent map needs to be constructed, as if the tracked files are modified during the construction of the result map, then
		//    the result map will probably be invalid, as linear time sorted construction might fail
		//we don't want to iterate over the children and call .put() for each of them as it will be slower for lot of files 
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		Iterator<Entry<String, SakerFileBase>> it = trackedfiles.entrySet().iterator();
		return ObjectUtils
				.createConcurrentSkipListMapFromSortedIterator(new PopulatedNotPresentFileOmittingEntryIterator(it));
	}

	@Override
	public NavigableMap<String, ? extends SakerFileContentInformationHolder> getChildrenContentInformation(
			DirectoryVisitPredicate childpredicate) {
		DirectoryVisitPredicate predicate;
		if (childpredicate == null) {
			predicate = DirectoryVisitPredicate.everything();
		} else {
			predicate = childpredicate;
		}
		ensurePopulated();

		//see getChildren comment

		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		Iterator<Entry<String, SakerFileBase>> it = trackedfiles.entrySet().iterator();
		Iterator<Entry<String, SakerFileContentInformationHolder>> contentinfoiterator = ObjectUtils
				.transformIterator(new PopulatedNotPresentFileOmittingEntryIterator(it), e -> {
					SakerFileBase file = e.getValue();
					String filename = e.getKey();
					ContentDescriptor cd;
					if (file instanceof SakerDirectory) {
						cd = DirectoryContentDescriptor.INSTANCE;
					} else if (predicate.visitFile(filename, file)) {
						cd = file.getContentDescriptor();
						if (cd == null) {
							//extra sanity check as the clients may depend on the nullability if this
							throw new NullPointerException("getContentDescriptor() returned null by: " + file);
						}
					} else {
						cd = null;
					}
					return ImmutableUtils.makeImmutableMapEntry(filename,
							new SakerFileContentInformationHolder(file, cd));
				});
		return ObjectUtils.createConcurrentSkipListMapFromSortedIterator(contentinfoiterator);
	}

	public NavigableSet<String> getChildrenNames() {
		ensurePopulated();
		//constructing concurrent set, see .getChildren() comment
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		return ObjectUtils.createConcurrentSkipListSetFromSortedIterator(
				new PopulatedNotPresentFileOmittingNameIterator(trackedfiles.entrySet().iterator()));
	}

	public NavigableMap<SakerPath, SakerFile> getFilesRecursiveByPath(SakerPath basepath) {
		return getFilesRecursiveByPath(basepath, DirectoryVisitPredicate.everything());
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> getFilesRecursiveByPath(SakerPath basepath,
			DirectoryVisitPredicate filepredicate) {
		ConcurrentSkipListMap<SakerPath, SakerFile> result = new ConcurrentSkipListMap<>();
		collectFilesRecursiveImpl(basepath, result, filepredicate);
		return result;
	}

	public final void collectFilesRecursive(ConcurrentMap<? super SakerPath, ? super SakerFile> result,
			DirectoryVisitPredicate filepredicate) {
		collectFilesRecursiveImpl(getSakerPath(), result, filepredicate);
	}

	public final void collectFilesRecursive(ConcurrentMap<? super SakerPath, ? super SakerFile> result) {
		collectFilesRecursive(result, DirectoryVisitPredicate.everything());
	}

	public final void collectFilesRecursive(Map<? super SakerPath, ? super SakerFile> result,
			DirectoryVisitPredicate filepredicate) {
		if (result instanceof ConcurrentMap) {
			collectFilesRecursive((ConcurrentMap<? super SakerPath, ? super SakerFile>) result, filepredicate);
		} else {
			ConcurrentSkipListMap<SakerPath, SakerFile> ccmapres = new ConcurrentSkipListMap<>();
			collectFilesRecursiveImpl(getSakerPath(), ccmapres, filepredicate);
			result.putAll(ccmapres);
		}
	}

	public final void collectFilesRecursive(Map<? super SakerPath, ? super SakerFile> result) {
		collectFilesRecursive(result, DirectoryVisitPredicate.everything());
	}

	@Override
	public SakerDirectory getDirectoryCreate(String name) {
		SakerPathFiles.requireValidFileName(name);
		return getDirectoryCreateImpl(name);
	}

	@Override
	public SakerDirectory getDirectoryCreateIfAbsent(String name) {
		SakerPathFiles.requireValidFileName(name);
		return createAndAddDirectoryIfAbsent(name);
	}

	@Override
	public void synchronize(DirectoryVisitPredicate synchpredicate) throws IOException {
		SakerPathFiles.synchronizeDirectory(this, synchpredicate, getContentDatabase());
	}

	@Override
	public void synchronize(ProviderHolderPathKey pathkey, DirectoryVisitPredicate synchpredicate) throws IOException {
		SakerPathFiles.synchronizeDirectory(this, pathkey, synchpredicate, getContentDatabase());
	}

	@Override
	void synchronizeInternal() throws IOException {
		SakerPathFiles.synchronizeDirectory(this, getContentDatabase());
	}

	@Override
	void synchronizeInternal(ProviderHolderPathKey pathkey) throws IOException {
		this.synchronize(pathkey, DirectoryVisitPredicate.everything());
	}

	@Override
	public void clear() {
		ConcurrentNavigableMap<String, SakerFileBase> files = getTrackedFiles();
		synchronized (files) {
			//synchronize so no population is being done concurrently
			populatedState = POPULATED_STATE_POPULATED;
		}
		while (true) {
			Entry<String, SakerFileBase> first = files.pollFirstEntry();
			if (first == null) {
				break;
			}
			SakerFileBase file = first.getValue();
			internal_casParent(file, this, MarkerSakerDirectory.REMOVED_FROM_PARENT);
		}
	}

	@Override
	public boolean isEmpty() {
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		for (SakerFileBase f : trackedfiles.values()) {
			if (f != MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return false;
			}
		}
		ensurePopulated();
		for (SakerFileBase f : trackedfiles.values()) {
			if (f != MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return false;
			}
		}
		return true;
	}

	public boolean isPopulated() {
		return populatedState == POPULATED_STATE_POPULATED;
	}

	public void ensurePopulated() {
		if (populatedState != POPULATED_STATE_POPULATED) {
			synchronized (getTrackedFiles()) {
				if (populatedState != POPULATED_STATE_POPULATED) {
					executePopulation();
				}
			}
		}
	}

	@Override
	public final ContentDescriptor getContentDescriptor() {
		return DirectoryContentDescriptor.INSTANCE;
	}

	protected final ConcurrentNavigableMap<String, SakerFileBase> getTrackedFiles() {
		return this.trackedFiles;
	}

	protected abstract NavigableMap<String, SakerFileBase> populateImpl();

	protected abstract SakerFileBase populateSingleImpl(String name);

	private final SakerFileBase populateSingle(String name) {
		AIFU_populatedState.compareAndSet(this, POPULATED_STATE_UNPOPULATED, POPULATED_STATE_PARTIALLY_POPULATED);
		return this.populateSingleImpl(name);
	}

	protected final void remove(SakerFileBase file) {
		if (!internal_casParent(file, this, MarkerSakerDirectory.REMOVED_FROM_PARENT)) {
			//file is not attached to this directory
			//do not throw an exception, as this can be called concurrently and can result in unexpected exceptions
			return;
		}
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		String filename = file.getName();
		if (populatedState == POPULATED_STATE_POPULATED) {
			trackedfiles.remove(filename, file);
		} else {
			boolean replaced = trackedfiles.replace(filename, file, MarkerSakerDirectory.POPULATED_NOT_PRESENT);
			if (replaced && populatedState == POPULATED_STATE_POPULATED) {
				//if the populated state changed meanwhile. let's not keep the marker in the map
				trackedfiles.remove(filename, MarkerSakerDirectory.POPULATED_NOT_PRESENT);
			}
		}
		//if we failed to remove the file that means that it has been already overwritten. this is fine. 
	}

	@Override
	public final void writeToStreamImpl(OutputStream os) throws IOException {
		throw unsupportedIO();
	}

	@Override
	public final ByteSource openByteSourceImpl() throws IOException {
		throw unsupportedIO();
	}

	@Override
	public final InputStream openInputStreamImpl() throws IOException {
		throw unsupportedIO();
	}

	@Override
	public final String getContentImpl() throws IOException {
		throw unsupportedIO();
	}

	@Override
	public final ByteArrayRegion getBytesImpl() throws IOException {
		throw unsupportedIO();
	}

	@Override
	public final int getEfficientOpeningMethods() {
		return OPENING_METHODS_ALL;
	}

	@Override
	public void synchronizeImpl(ProviderHolderPathKey pathkey) throws IOException {
		this.synchronize(pathkey, DirectoryVisitPredicate.everything());
	}

	@Override
	public boolean synchronizeImpl(ProviderHolderPathKey pathkey, ByteSink additionalwritestream)
			throws SecondaryStreamException, IOException {
		this.synchronize(pathkey, DirectoryVisitPredicate.everything());
		return false;
	}

	private SakerFileBase putIfAbsentImpl(ConcurrentNavigableMap<String, SakerFileBase> trackedfiles,
			SakerFileBase file) {
		String filename = file.getName();
		if (populatedState == POPULATED_STATE_POPULATED) {
			return putIfAbsentImplForPopulatedState(trackedfiles, file, filename);
		}
		SakerFileBase v = trackedfiles.get(filename);
		Supplier<SakerFileBase> populater = LazySupplier.of(() -> {
			SakerFileBase popresult = populateSingle(filename);
			if (popresult != null) {
				SakerFileBase.internal_setParent(popresult, this);
			}
			return popresult;
		});
		while (true) {
			if (v != null) {
				if (v == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
					if (!trackedfiles.replace(filename, v, file)) {
						//try again
						v = trackedfiles.get(filename);
						continue;
					}
					//the file was put in place, success
					return null;
				}
				//already present, return the previous
				return v;
			}
			SakerFileBase populated = populater.get();
			if (populated == null) {
				SakerFileBase prev = trackedfiles.putIfAbsent(filename, file);
				if (prev != null) {
					//try again
					v = prev;
					continue;
				}
				//the file was put in place, success
				return null;
			}
			synchronized (trackedfiles) {
				//synchronize to ensure that any concurrent population requests finish before we put the populated file
				if (populatedState != POPULATED_STATE_POPULATED) {
					//still unpopulated, put the file
					SakerFileBase prev = trackedfiles.putIfAbsent(filename, populated);
					if (prev != null) {
						//try again
						v = prev;
						continue;
					}
					//the populated file was put in place, put if absent failed
					return populated;
				}
				//don't use the populated file, as the full population completed meanwhile
			}
			//the directory was populated meanwhile
			//perform the absent insertion for the populated state
			return putIfAbsentImplForPopulatedState(trackedfiles, file, filename);
		}
	}

	private static SakerFileBase putIfAbsentImplForPopulatedState(
			ConcurrentNavigableMap<String, SakerFileBase> trackedfiles, SakerFileBase file, String filename) {
		SakerFileBase result = trackedfiles.compute(filename, (k, v) -> {
			if (v == null || v == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return file;
			}
			return v;
		});
		if (result == file) {
			//the file was put in place. no previous, return null.
			return null;
		}
		return result;
	}

	private void executePopulation() {
		Map<String, SakerFileBase> populatedfiles = this.populateImpl();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();

		if (!populatedfiles.isEmpty()) {
			for (Entry<String, SakerFileBase> entry : populatedfiles.entrySet()) {
				SakerFileBase file = entry.getValue();
				SakerFileBase.internal_setParent(file, this);
				String filename = entry.getKey();
				trackedfiles.compute(filename, (k, v) -> {
					if (v == null) {
						return file;
					}
					if (v == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
						//if v == MarkerSakerDirectory.POPULATED_NOT_PRESENT
						//  then we return null to remove the mapping, as we already noticed that the file was not present
						//  for population. this population call shouldn't add it back
						return null;
					}
					return v;
				});
			}
		}
		populatedState = POPULATED_STATE_POPULATED;
		for (Iterator<SakerFileBase> it = trackedfiles.values().iterator(); it.hasNext();) {
			SakerFileBase f = it.next();
			if (f == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				it.remove();
			}
		}
	}

	private SakerFileBase getImpl(String name) {
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		SakerFileBase got = trackedfiles.get(name);
		if (got != null) {
			if (got == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return null;
			}
			return got;
		}
		if (populatedState == POPULATED_STATE_POPULATED) {
			got = trackedfiles.get(name);
			if (got == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return null;
			}
			return got;
		}
		SakerFileBase populated = populateSingle(name);
		if (populated != null) {
			SakerFileBase.internal_setParent(populated, this);
			synchronized (trackedfiles) {
				//synchronize to ensure that any concurrent population requests finish before we put the populated file
				if (populatedState != POPULATED_STATE_POPULATED) {
					//still unpopulated, put the file
					SakerFileBase prev = trackedfiles.putIfAbsent(name, populated);
					if (prev != null) {
						if (prev == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
							return null;
						}
						return prev;
					}
					return populated;
				}
			}
			//the directory was populated meanwhile. simply get.
			got = trackedfiles.get(name);
			if (got == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return null;
			}
			return got;
		}
		SakerFileBase prev = trackedfiles.putIfAbsent(name, MarkerSakerDirectory.POPULATED_NOT_PRESENT);
		if (prev != null) {
			if (prev == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return null;
			}
			return prev;
		}
		return null;
	}

	private final SakerDirectory getDirectoryCreateImpl(String name) {
		SakerFileBase existing = getImpl(name);
		if (existing instanceof SakerDirectory) {
			return (SakerDirectory) existing;
		}
		return createAndAddDirectory(existing, name);
	}

	private SakerDirectory createAndAddDirectory(SakerFileBase existing, String name) {
		SakerDirectoryBase add = new SimpleSakerDirectory(name);
		SakerFileBase.internal_setParent(add, this);

		final ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		do {
			if (existing == null) {
				SakerFileBase prev = trackedfiles.compute(name, (k, v) -> {
					if (v == null || v == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
						return add;
					}
					return v;
				});
				if (prev == add) {
					//successfully inserted
					return add;
				}
				//failed to insert
				existing = prev;
				continue;
			}
			//next is non-null and not a directory
			boolean success = trackedfiles.replace(name, existing, add);
			if (success) {
				internal_casParent(existing, this, MarkerSakerDirectory.REMOVED_FROM_PARENT);
				return add;
			}
			existing = getImpl(name);
			//failed to replace
		} while (!(existing instanceof SakerDirectory));
		return (SakerDirectory) existing;
	}

	private SakerDirectory createAndAddDirectoryIfAbsent(String name) {
		SakerFileBase existing = getImpl(name);
		if (existing != null) {
			if (existing instanceof SakerDirectory) {
				return (SakerDirectory) existing;
			}
			//file already exists, but not a directory
			return null;
		}

		SakerDirectoryBase add = new SimpleSakerDirectory(name);
		SakerFileBase.internal_setParent(add, this);

		final ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		SakerFileBase prev = trackedfiles.compute(name, (k, v) -> {
			if (v == null || v == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				return add;
			}
			return v;
		});
		if (prev == add) {
			//successfully inserted
			return add;
		}
		//failed to insert, a file is already there
		if (prev instanceof SakerDirectory) {
			return (SakerDirectory) prev;
		}
		//file already exists, but not a directory
		return null;
	}

	private void collectFilesRecursiveImpl(SakerPath dirpath,
			ConcurrentMap<? super SakerPath, ? super SakerFile> result, DirectoryVisitPredicate filepredicate) {
		//XXX this task pool could be something common in the task context or something, so we don't create too many threads
		try (ThreadWorkPool collectorpool = ThreadUtils.newFixedWorkPool()) {
			collectFilesRecursiveImpl(dirpath, result, filepredicate, collectorpool);
		}
	}

	private void collectFilesRecursiveImpl(SakerPath dirpath,
			ConcurrentMap<? super SakerPath, ? super SakerFile> result, DirectoryVisitPredicate filepredicate,
			ThreadWorkPool collectorpool) {
		final DirectoryVisitPredicate ffilepredicate;
		if (filepredicate == null) {
			ffilepredicate = DirectoryVisitPredicate.everything();
		} else {
			ffilepredicate = filepredicate;
		}
		ensurePopulated();
		for (SakerFileBase file : getTrackedFiles().values()) {
			if (file == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
				continue;
			}
			String fname = file.getName();
			if (file instanceof SakerDirectory) {
				SakerDirectoryBase childdir = (SakerDirectoryBase) file;
				boolean visitdir = ffilepredicate.visitDirectory(fname, childdir);
				DirectoryVisitPredicate dirvisitor = ffilepredicate.directoryVisitor(fname, childdir);
				if (!visitdir && dirvisitor == null) {
					continue;
				}

				SakerPath subdirpath = dirpath.resolve(fname);
				if (visitdir) {
					result.put(subdirpath, file);
				}
				if (dirvisitor != null) {
					if (childdir.isPopulated()) {
						childdir.collectFilesRecursiveImpl(subdirpath, result, dirvisitor, collectorpool);
					} else {
						collectorpool.offer(() -> {
							childdir.collectFilesRecursiveImpl(subdirpath, result, dirvisitor, collectorpool);
						});
					}
				}
			} else {
				if (ffilepredicate.visitFile(fname, file)) {
					result.put(dirpath.resolve(fname), file);
				}
			}
		}
	}

	private static IOException unsupportedIO() {
		return new IOException("unsupported for directories.");
	}

	private static class PopulatedNotPresentFileOmittingEntryIterator
			implements Iterator<Entry<String, SakerFileBase>> {
		private Iterator<Entry<String, SakerFileBase>> it;
		private Entry<String, SakerFileBase> next;

		public PopulatedNotPresentFileOmittingEntryIterator(Iterator<Entry<String, SakerFileBase>> it) {
			this.it = it;
			moveToNext();
		}

		private void moveToNext() {
			next = null;
			while (it.hasNext()) {
				Entry<String, SakerFileBase> n = it.next();
				if (n.getValue() == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
					continue;
				}
				next = n;
				break;
			}
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public Entry<String, SakerFileBase> next() {
			Entry<String, SakerFileBase> n = next;
			if (n == null) {
				throw new NoSuchElementException();
			}
			moveToNext();
			return n;
		}
	}

	private static class PopulatedNotPresentFileOmittingNameIterator implements Iterator<String> {
		private Iterator<Entry<String, SakerFileBase>> it;
		private String next;

		public PopulatedNotPresentFileOmittingNameIterator(Iterator<Entry<String, SakerFileBase>> it) {
			this.it = it;
			moveToNext();
		}

		private void moveToNext() {
			next = null;
			while (it.hasNext()) {
				Entry<String, SakerFileBase> n = it.next();
				if (n.getValue() == MarkerSakerDirectory.POPULATED_NOT_PRESENT) {
					continue;
				}
				next = n.getKey();
				break;
			}
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public String next() {
			String n = next;
			if (n == null) {
				throw new NoSuchElementException();
			}
			moveToNext();
			return n;
		}
	}
}
