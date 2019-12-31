package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
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
			if (internal_getParent(basef) == RemovedMarkerSakerDirectory.INSTANCE) {
				throw new IllegalStateException("File was already removed from its parent: " + file);
			}
			throw new IllegalStateException("File already has a parent: " + file);
		}

		String filename = file.getName();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		SakerFileBase prev = trackedfiles.put(filename, basef);
		if (prev != null) {
			internal_casParent(prev, this, RemovedMarkerSakerDirectory.INSTANCE);
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

	private SakerFileBase putIfAbsentImpl(ConcurrentNavigableMap<String, SakerFileBase> trackedfiles,
			SakerFileBase file) {
		String filename = file.getName();
		if (populatedState == POPULATED_STATE_POPULATED) {
			return trackedfiles.putIfAbsent(filename, file);
		}
		//not populated so populate the file with the name
		SakerFileBase populated = populateSingle(filename);
		if (populated == null) {
			//no file would be populated, can use putIfAbsent accordingly
			return trackedfiles.putIfAbsent(filename, file);
		}
		SakerFileBase.internal_setParent(populated, this);
		SakerFileBase prev = trackedfiles.putIfAbsent(filename, populated);
		if (prev != null) {
			return prev;
		}
		return populated;
	}

	@Override
	public SakerFile addIfAbsent(SakerFile file) throws NullPointerException, IllegalStateException {
		Objects.requireNonNull(file, "file");
		SakerFileBase basef = (SakerFileBase) file;
		if (!internal_casParent(basef, null, this)) {
			if (internal_getParent(basef) == RemovedMarkerSakerDirectory.INSTANCE) {
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
			if (internal_getParent(basef) == RemovedMarkerSakerDirectory.INSTANCE) {
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
		return new ConcurrentSkipListMap<>(getTrackedFiles());
	}

	public NavigableSet<String> getChildrenNames() {
		ensurePopulated();
		//constructing concurrent set, see .getChildren() comment
		return new ConcurrentSkipListSet<>(getTrackedFiles().navigableKeySet());
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
		if (files.isEmpty()) {
			return;
		}
		while (true) {
			Entry<String, SakerFileBase> first = files.pollFirstEntry();
			if (first == null) {
				break;
			}
			SakerFileBase file = first.getValue();
			internal_casParent(file, this, RemovedMarkerSakerDirectory.INSTANCE);
		}
	}

	@Override
	public boolean isEmpty() {
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		if (!trackedfiles.isEmpty()) {
			return false;
		}
		ensurePopulated();
		return trackedfiles.isEmpty();
	}

	public boolean isPopulated() {
		return populatedState == POPULATED_STATE_POPULATED;
	}

//	public final void populate() {
//		// forced populate request
//		synchronized (getTrackedFiles()) {
//			executePopulation();
//		}
//	}
//
//	public final SakerFile populate(String name) {
//		SakerFile got = get(name);
//		if (got != null) {
//			return got;
//		}
//		// forced populate request
//		ConcurrentNavigableMap<String, SakerFileBase> populatedfiles = getTrackedFiles();
//		SakerFileBase pop = populateSingle(name);
//		if (pop == null) {
//			return null;
//		}
//		SakerFileBase.internal_setParent(pop, this);
//		SakerFile prev = populatedfiles.putIfAbsent(name, pop);
//		if (prev != null) {
//			return prev;
//		}
//		return pop;
//	}

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
	public ContentDescriptor getContentDescriptor() {
		return DirectoryContentDescriptor.INSTANCE;
	}

	protected final ConcurrentNavigableMap<String, SakerFileBase> getTrackedFiles() {
		return this.trackedFiles;
	}

	protected abstract Map<String, SakerFileBase> populateImpl();

	protected abstract SakerFileBase populateSingleImpl(String name);

	private final SakerFileBase populateSingle(String name) {
		AIFU_populatedState.compareAndSet(this, POPULATED_STATE_UNPOPULATED, POPULATED_STATE_PARTIALLY_POPULATED);
		//TODO if there was no file found, then we should populate the whole directory so the absence of the file is stored accordingly
		return this.populateSingleImpl(name);
	}

	protected final void remove(SakerFileBase file) {
		if (!internal_casParent(file, this, RemovedMarkerSakerDirectory.INSTANCE)) {
			//file is not attached to this directory
			//do not throw an exception, as this can be called concurrently and can result in unexpected exceptions
			return;
		}
		//we need to populate the directory so the file removal is actually noted as it being removed
		//if we dont populate, then a new get() call with the file name would repopulate the removed file
		ensurePopulated();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		String filename = file.getName();
		trackedfiles.remove(filename, file);
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

	private void executePopulation() {
		Map<String, SakerFileBase> files = this.populateImpl();
		ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();

		if (!files.isEmpty()) {
			for (Entry<String, SakerFileBase> entry : files.entrySet()) {
				SakerFileBase file = entry.getValue();
				SakerFileBase.internal_setParent(file, this);
				String filename = entry.getKey();
				trackedfiles.putIfAbsent(filename, file);
			}
		}
		populatedState = POPULATED_STATE_POPULATED;
	}

	private SakerFileBase getImpl(String name) {
		ConcurrentNavigableMap<String, SakerFileBase> files = getTrackedFiles();
		SakerFileBase got = files.get(name);
		if (got != null) {
			return got;
		}
		if (populatedState == POPULATED_STATE_POPULATED) {
			//there is a race condition while getting the file and checking if populated
			//so check again just in case
			return files.get(name);
		}
		SakerFileBase populated = populateSingle(name);
		if (populated != null) {
			SakerFileBase.internal_setParent(populated, this);
			SakerFileBase prev = files.putIfAbsent(name, populated);
			if (prev != null) {
				return prev;
			}
			return populated;
		}
		return null;
	}

	private final SakerDirectory getDirectoryCreateImpl(String name) {
		SakerFileBase dir = getImpl(name);
		if (dir != null && dir instanceof SakerDirectory) {
			return (SakerDirectory) dir;
		}
		return createAndAddDirectory(dir, name);
	}

	private SakerDirectory createAndAddDirectory(SakerFileBase existing, String name) {
		SakerDirectoryBase add = new SimpleSakerDirectory(name);
		SakerFileBase.internal_setParent(add, this);

		final ConcurrentNavigableMap<String, SakerFileBase> trackedfiles = getTrackedFiles();
		do {
			if (existing == null) {
				SakerFileBase prev = trackedfiles.putIfAbsent(name, add);
				if (prev == null) {
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
				internal_casParent(existing, this, RemovedMarkerSakerDirectory.INSTANCE);
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
		SakerFileBase prev = trackedfiles.putIfAbsent(name, add);
		if (prev == null) {
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
		//XXX use thread pool and not smart parallel
		for (SakerFileBase file : getTrackedFiles().values()) {
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
//		ThreadUtils.smartParallel(getTrackedFiles().values(), file -> {
//			String fname = file.getName();
//			if (file instanceof SakerDirectory) {
//				SakerDirectoryBase childdir = (SakerDirectoryBase) file;
//				boolean visitdir = ffilepredicate.visitDirectory(fname, childdir);
//				DirectoryVisitPredicate dirvisitor = ffilepredicate.directoryVisitor(fname, childdir);
//				if (!visitdir && dirvisitor == null) {
//					return null;
//				}
//
//				SakerPath subdirpath = dirpath.resolve(fname);
//				if (visitdir) {
//					result.put(subdirpath, file);
//				}
//				if (dirvisitor != null) {
//					if (childdir.isPopulated()) {
//						childdir.collectFilesRecursiveImpl(subdirpath, result, dirvisitor);
//					} else {
//						return () -> {
//							childdir.collectFilesRecursiveImpl(subdirpath, result, dirvisitor);
//						};
//					}
//				}
//			} else {
//				if (ffilepredicate.visitFile(fname, file)) {
//					result.put(dirpath.resolve(fname), file);
//				}
//			}
//			return null;
//		});
	}

	private static IOException unsupportedIO() {
		return new IOException("unsupported for directories.");
	}
}
