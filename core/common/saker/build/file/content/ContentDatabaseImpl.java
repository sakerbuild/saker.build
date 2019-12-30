package saker.build.file.content;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

import saker.build.exception.InvalidFileTypeException;
import saker.build.file.SecondaryStreamException;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimplePathKey;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.BuildUserPromptHandler;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.BuildTaskResultDatabase;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskExecutionResult.FileDependencies;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.PriorityMultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.io.function.IORunnable;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;

public class ContentDatabaseImpl implements ContentDatabase, Closeable {
	//methods with Offline means they are called when no execution is running, so further optimizations can be employed

	private final class DeferredSynchronizerImpl implements DeferredSynchronizer {
		private final ContentHandleImpl handle;
		private ContentDescriptor expectContentIdentity;

		private final ProviderHolderPathKey pathkey;
		private final ContentDescriptor content;
		private final ContentUpdater updater;

		private DeferredSynchronizerImpl(ContentHandleImpl handle, ContentDescriptor content,
				ContentDescriptor checkcontent, ProviderHolderPathKey pathkey, ContentUpdater updater) {
			this.handle = handle;
			this.content = content;
			this.expectContentIdentity = checkcontent;
			this.pathkey = pathkey;
			this.updater = updater;
		}

		@Override
		public void update() throws IOException {
			RootFileProviderKey fpkey = pathkey.getFileProviderKey();
			SakerPath path = pathkey.getPath();
			synchronized (getPathLock(fpkey, path)) {
				ContentDescriptor currentcontent = handle.getContent();
				if (currentcontent == expectContentIdentity || content.isChanged(currentcontent)) {
					executeSynchronizeLocked(pathkey.getFileProvider(), path, content, updater, handle, fpkey, pathkey);
					//if update is called again, we should expect the synchronized content 
					this.expectContentIdentity = this.content;
				} else {
					//the contents are the same, or not changed
					//expect the current one when called next
					this.expectContentIdentity = currentcontent;
				}
			}
		}
	}

	public static class PathProtectionSettings {
		protected Map<RootFileProviderKey, NavigableSet<SakerPath>> writeEnabledDirectories;
		protected BuildUserPromptHandler prompter;

		public PathProtectionSettings(Map<RootFileProviderKey, NavigableSet<SakerPath>> writeEnabledDirectories,
				BuildUserPromptHandler prompter) {
			this.writeEnabledDirectories = writeEnabledDirectories;
			this.prompter = prompter;
		}
	}

	private static class UserContentState {
		private final ContentDescriptor userContent;
		private final ContentDescriptor userExpectedDiskContent;

		public UserContentState(ContentDescriptor userContent, ContentDescriptor userExpectedDiskContent) {
			this.userContent = userContent;
			this.userExpectedDiskContent = userExpectedDiskContent;
		}
	}

	private static final class ContentHandleImpl implements ContentHandle {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ContentDatabaseImpl.ContentHandleImpl, Supplier> ARFU_diskContent = AtomicReferenceFieldUpdater
				.newUpdater(ContentDatabaseImpl.ContentHandleImpl.class, Supplier.class, "diskContent");

		private final ContentDatabaseImpl db;
		private final ProviderHolderPathKey pathKey;
		private final ContentDescriptorSupplier contentSupplier;

		private volatile UserContentState userContent;
		private volatile Supplier<? extends ContentDescriptor> diskContent;
		private BasicFileAttributes diskAttributes;

		public ContentHandleImpl(ContentDatabaseImpl db, ProviderHolderPathKey pathKey,
				ContentDescriptorSupplier contentsupplier) {
			this.db = db;
			this.pathKey = pathKey;
			this.userContent = null;
			this.contentSupplier = contentsupplier;
			this.diskContent = null;
		}

		public ContentHandleImpl(ContentDatabaseImpl db, ContentDescriptor usercontent,
				ContentDescriptor userexpecteddiskcontent, ProviderHolderPathKey pathKey,
				ContentDescriptorSupplier contentsupplier) {
			this.db = db;
			this.pathKey = pathKey;
			this.userContent = new UserContentState(usercontent, userexpecteddiskcontent);
			this.contentSupplier = contentsupplier;
			this.diskContent = null;
		}

		private LazySupplier<ContentDescriptor> createDiskContentSupplier() {
			return LazySupplier.of(() -> {
				try {
					return contentSupplier.get(pathKey);
				} catch (FileNotFoundException | NoSuchFileException e) {
				} catch (IOException e) {
					//XXX do we need to print the exception here?
					e.printStackTrace();
				}
				return null;
			});
		}

		@Override
		public ContentDatabase getContentDatabase() {
			return db;
		}

		//don't set the disk content to null during invalidation, as we don't want discovery to reset the contents anymore

		public synchronized void invalidate() {
			this.diskContent = createDiskContentSupplier();
			this.diskAttributes = null;
		}

		public void invalidateOffline() {
			this.diskContent = createDiskContentSupplier();
			this.diskAttributes = null;
		}

		public void invalidateHardOffline() {
			this.diskContent = createDiskContentSupplier();
			this.diskAttributes = null;
			this.userContent = null;
		}

		public void setDiskAttributesOffline(BasicFileAttributes diskattributes) {
			this.diskContent = createDiskContentSupplier();
			this.diskAttributes = diskattributes;
		}

		public synchronized void setContent(ContentDescriptor usercontent, ContentDescriptor diskcontent,
				BasicFileAttributes diskattributes) {
			this.userContent = new UserContentState(usercontent, diskcontent);
			this.diskContent = Functionals.valSupplier(diskcontent);
			this.diskAttributes = diskattributes;
		}

		public synchronized void discoverAttributes(FileEntry attributes, FileEntry trackattributes) {
			if (contentSupplier != CommonContentDescriptorSupplier.FILE_ATTRIBUTES) {
				if (this.diskContent == null) {
					this.diskAttributes = trackattributes;
				}
				return;
			}
			//only discover the attributes if the file was not yet accessed by an other agent
			if (ARFU_diskContent.compareAndSet(this, null,
					Functionals.valSupplier(FileAttributesContentDescriptor.create(pathKey, attributes)))) {
				this.diskAttributes = trackattributes;
			}
		}

		@Override
		public ContentDescriptor getContent() {
			Supplier<? extends ContentDescriptor> diskcontentsupplier = diskContent;
			if (diskcontentsupplier == null) {
				LazySupplier<ContentDescriptor> nsupplier = createDiskContentSupplier();
				if (ARFU_diskContent.compareAndSet(this, null, nsupplier)) {
					diskcontentsupplier = nsupplier;
				} else {
					diskcontentsupplier = this.diskContent;
				}
			}
			ContentDescriptor currentdiskcontent = diskcontentsupplier.get();
			if (currentdiskcontent == null) {
				return null;
			}
			UserContentState usercontent = userContent;
			if (usercontent == null) {
				return currentdiskcontent;
			}
			if (!currentdiskcontent.isChanged(usercontent.userExpectedDiskContent)) {
				return usercontent.userContent;
			}
			return currentdiskcontent;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + pathKey.getFileProviderKey() + " : " + pathKey.getPath() + "]";
		}

	}

	public static final String DATABASE_EXTENSION = ".map";
	public static final String FILENAME_DATABASE = "dependencies" + DATABASE_EXTENSION;

	private static final int OUTPUT_BUFFER_SIZE = 4 * 1024 * 1024;
	private static final int INPUT_BUFFER_SIZE = 4 * 1024 * 1024;

	private static final ToIntBiFunction<SakerPath, Entry<SakerPath, ?>> PATH_ENTRYKEY_PATH_COMPARATOR = (p,
			chentry) -> p.compareTo(chentry.getKey());

	private final DatabaseConfiguration databaseConfiguration;
	private final ExecutionPathConfiguration pathConfiguration;

	private final SakerFileProvider descriptorsFileProvider;
	private final SakerPath descriptorsFilePath;
	private final PathKey descriptorsPathKey;

	private final transient LocalFileProvider localFiles = LocalFileProvider.getInstance();
	private final transient RootFileProviderKey localFilesProviderKey = LocalFileProvider.getProviderKeyStatic();

	private final ConcurrentHashMap<RootFileProviderKey, ConcurrentSkipListMap<SakerPath, ContentHandleImpl>> providerKeyPathDependencies = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<RootFileProviderKey, ConcurrentSkipListMap<SakerPath, Object>> providerKeyPathUpdateLocks = new ConcurrentHashMap<>();

	private volatile boolean dirty = false;

	private BuildTaskResultDatabase taskResults = new BuildTaskResultDatabase();

	private ClassLoaderResolver classLoaderResolver;

	private boolean trackHandleAttributes = false;

	private PathProtectionSettings protectionSettings;

	public ContentDatabaseImpl(DatabaseConfiguration databaseconfig, ExecutionPathConfiguration pathconfig) {
		Objects.requireNonNull(databaseconfig, "database configuration");
		Objects.requireNonNull(pathconfig, "path configuration");

		this.pathConfiguration = pathconfig;
		this.databaseConfiguration = databaseconfig;

		this.descriptorsFilePath = null;
		this.descriptorsFileProvider = null;
		this.descriptorsPathKey = null;
	}

	public ContentDatabaseImpl(DatabaseConfiguration databaseconfig, ExecutionPathConfiguration pathconfig,
			ClassLoaderResolver clregistry, ProviderHolderPathKey databasepath) {
		Objects.requireNonNull(databaseconfig, "database configuration");
		Objects.requireNonNull(pathconfig, "path configuration");
		Objects.requireNonNull(clregistry, "class loader resolver");

		this.pathConfiguration = pathconfig;
		this.databaseConfiguration = databaseconfig;
		this.classLoaderResolver = clregistry;

		this.descriptorsFileProvider = databasepath.getFileProvider();
		this.descriptorsFilePath = databasepath.getPath();
		this.descriptorsPathKey = databasepath;

		try {
			read(pathconfig);
		} catch (Exception e) {
			//failed to read, ignored.
			e.printStackTrace();
		}
	}

	public boolean isDatabaseFileExists() {
		if (descriptorsPathKey == null) {
			return false;
		}
		try {
			return descriptorsFileProvider.getFileAttributes(descriptorsFilePath).isRegularFile();
		} catch (IOException | RMIRuntimeException e) {
			return false;
		}
	}

	public ClassLoaderResolver getClassLoaderResolver() {
		return classLoaderResolver;
	}

	public void setProtectionSettings(PathProtectionSettings protectionSettings) {
		this.protectionSettings = protectionSettings;
	}

	public DatabaseConfiguration getDatabaseConfiguration() {
		return databaseConfiguration;
	}

	@Override
	public ExecutionPathConfiguration getPathConfiguration() {
		return pathConfiguration;
	}

	public void setTrackHandleAttributes(boolean trackHandleAttributes) {
		this.trackHandleAttributes = trackHandleAttributes;
	}

	private ContentDescriptorSupplier getContentDescriptorSupplier(RootFileProviderKey providerkey, SakerPath path) {
		return databaseConfiguration.getContentDescriptorSupplier(providerkey, path);
	}

	public void handleAbandonedTasksOffline(ExecutionPathConfiguration pathconfig, SakerPath builddirectory) {
		Map<TaskIdentifier, TaskExecutionResult<?>> abandonedresults = taskResults.takeAbandonedTaskIdResults();
		if (builddirectory == null || abandonedresults.isEmpty()) {
			return;
		}
		ProviderHolderPathKey actualbuilddirpathkey = pathconfig.getPathKey(builddirectory);
		RootFileProviderKey actualbuilddirfpk = actualbuilddirpathkey.getFileProviderKey();
		Collection<TaskExecutionResult<?>> abandonedtaskresults = abandonedresults.values();
		NavigableMap<SakerPath, ContentDescriptor> filestodelete = new TreeMap<>();
		for (TaskExecutionResult<?> taskres : abandonedtaskresults) {
			SakerPath abandonedbuilddir = taskres.getExecutionBuildDirectory();
			if (abandonedbuilddir == null) {
				continue;
			}
			Map<Object, FileDependencies> taggeddeps = taskres.getDependencies().getTaggedFileDependencies();
			if (taggeddeps.isEmpty()) {
				continue;
			}
			PathKey builddirpathkey = taskres.getExecutionBuildDirectoryPathKey();
			RootFileProviderKey fpk = builddirpathkey.getFileProviderKey();
			if (!actualbuilddirfpk.equals(fpk)) {
				continue;
			}
			for (FileDependencies filedep : taggeddeps.values()) {
				NavigableMap<SakerPath, ContentDescriptor> outputdeps = filedep.getOutputFileDependencies();
				for (Entry<SakerPath, ContentDescriptor> entry : outputdeps.entrySet()) {
					SakerPath outputfilepath = entry.getKey();
					if (outputfilepath.isRelative()) {
						outputfilepath = taskres.getExecutionWorkingDirectory().resolve(outputfilepath);
					}
					if (!outputfilepath.startsWith(abandonedbuilddir)) {
						//output file is not under the build directory
						continue;
					}
					filestodelete.put(builddirpathkey.getPath().resolve(abandonedbuilddir.relativize(outputfilepath)),
							entry.getValue());
				}
			}
		}
		if (!filestodelete.isEmpty()) {
			for (TaskExecutionResult<?> taskres : taskResults.getTaskIdTaskResults().values()) {
				SakerPath taskbuilddir = taskres.getExecutionBuildDirectory();
				if (taskbuilddir == null) {
					continue;
				}
				Map<Object, FileDependencies> taggeddeps = taskres.getDependencies().getTaggedFileDependencies();
				if (taggeddeps.isEmpty()) {
					continue;
				}
				PathKey builddirpathkey = taskres.getExecutionBuildDirectoryPathKey();
				RootFileProviderKey fpk = builddirpathkey.getFileProviderKey();
				if (!actualbuilddirfpk.equals(fpk)) {
					//this should not actually ever happen, as the currently present tasks should always have the 
					//execution build directory as its task build directory
					//but better safe than sorry
					continue;
				}
				for (FileDependencies filedep : taggeddeps.values()) {
					NavigableMap<SakerPath, ContentDescriptor> outputdeps = filedep.getOutputFileDependencies();
					for (Entry<SakerPath, ContentDescriptor> entry : outputdeps.entrySet()) {
						SakerPath outputfilepath = entry.getKey();
						if (outputfilepath.isRelative()) {
							outputfilepath = taskres.getExecutionWorkingDirectory().resolve(outputfilepath);
						}
						if (!outputfilepath.startsWith(taskbuilddir)) {
							//output file is not under the build directory
							continue;
						}
						filestodelete
								.remove(builddirpathkey.getPath().resolve(taskbuilddir.relativize(outputfilepath)));
					}
				}
			}
			SakerFileProvider fileprovider = actualbuilddirpathkey.getFileProvider();
			SakerPath actualbuilddirpath = actualbuilddirpathkey.getPath();
			while (!filestodelete.isEmpty()) {
				Entry<SakerPath, ContentDescriptor> delentry = filestodelete.pollFirstEntry();
				try {
					SakerPath delpath = delentry.getKey();
					//just one more sanity check to avoid deleting files that are not under the build directory
					if (!delpath.startsWith(actualbuilddirpath)) {
						continue;
					}
					deleteWithContent(actualbuilddirfpk, delpath, delentry.getValue(), () -> {
						fileprovider.deleteRecursively(delpath);
					});
				} catch (IOException | RMIRuntimeException e) {
					//catch RMI exceptions in case of remote file provider
					e.printStackTrace();
				}
			}
			//XXX might be paralellizable

		}
	}

	public boolean isConfiguredTo(ExecutionPathConfiguration pathconfig, PathKey path, DatabaseConfiguration dbconfig) {
		return Objects.equals(path, this.descriptorsPathKey)
				&& pathconfig.isSameProviderConfiguration(this.pathConfiguration)
				&& Objects.equals(databaseConfiguration, dbconfig);
	}

	public boolean isPersisting() {
		return descriptorsFilePath != null;
	}

	public BuildTaskResultDatabase getTaskResultDatabase() {
		return taskResults;
	}

	public synchronized void setTaskResults(BuildTaskResultDatabase taskresults) {
		if (taskresults == this.taskResults) {
			//unchanged
			return;
		}
		this.taskResults = taskresults;
		setDirty();
	}

	private void setDirty() {
		this.dirty = true;
	}

	public synchronized void flush() throws IOException {
		if (!isPersisting()) {
			return;
		}

		synchronized (this) {
			if (!this.dirty) {
				return;
			}
			this.dirty = false;
		}
		descriptorsFileProvider.createDirectories(descriptorsFilePath.getParent());
		try (OutputStream descos = new UnsyncBufferedOutputStream(
				ByteSink.toOutputStream(descriptorsFileProvider.openOutput(descriptorsFilePath)), OUTPUT_BUFFER_SIZE);
				ContentWriterObjectOutput descobjout = new ContentWriterObjectOutput(classLoaderResolver)) {
			for (Entry<RootFileProviderKey, ConcurrentSkipListMap<SakerPath, ContentHandleImpl>> entry : providerKeyPathDependencies
					.entrySet()) {
				RootFileProviderKey fpk = entry.getKey();
				descobjout.writeObject(fpk);
				descobjout.drainTo(descos);

				writeDependencies(entry.getValue(), descobjout, descos);
			}
			descobjout.writeNull();
			descobjout.drainTo(descos);

			//write the key value pairs one by one and drain them
			descobjout.writeObject(taskResults);
			descobjout.drainTo(descos);
		} catch (Exception e) {
			setDirty();
			throw new IOException(e);
		}
	}

	private static void writeDependencies(ConcurrentSkipListMap<SakerPath, ContentHandleImpl> dependencies,
			ContentWriterObjectOutput descobjout, OutputStream descos) throws IOException {
		SakerPath relative = null;
		for (Iterator<Entry<SakerPath, ContentHandleImpl>> it = dependencies.entrySet().iterator(); it.hasNext();) {
			Entry<SakerPath, ContentHandleImpl> entry = it.next();
			ContentHandleImpl dep = entry.getValue();
			UserContentState usercontent = dep.userContent;
			if (usercontent == null) {
				continue;
			}
			SakerPath path = entry.getKey();

			try {
				if (relative != null && path.startsWith(relative)) {
					SakerPath relativized = relative.relativize(path);
					descobjout.writeObject(relativized);
				} else {
					descobjout.writeObject(path);
				}
				relative = path.getParent();

				descobjout.writeObject(usercontent.userExpectedDiskContent);
				descobjout.writeObject(usercontent.userContent);
			} catch (IOException e) {
				e.printStackTrace();
			}
			//this can throw, so that should be propagated to the caller
			descobjout.drainTo(descos);
		}
		//empty path last item marker
		descobjout.writeObject(null);
		descobjout.drainTo(descos);
	}

	private void read(ExecutionPathConfiguration pathconfig) {
		if (!isPersisting()) {
			return;
		}
		//use a big buffer for reading the file contents
		//this reduces the amount of kernel calls (if no default buffering is provided by the Java layer)
		try (InputStream descis = new UnsyncBufferedInputStream(
				ByteSource.toInputStream(descriptorsFileProvider.openInput(descriptorsFilePath)), INPUT_BUFFER_SIZE);
				ContentReaderObjectInput reader = new ContentReaderObjectInput(classLoaderResolver, descis)) {
			while (true) {
				RootFileProviderKey fpkey;
				try {
					fpkey = (RootFileProviderKey) reader.readObject();
					if (fpkey == null) {
						break;
					}
				} catch (EOFException e) {
					e.printStackTrace();
					break;
				}
				SakerFileProvider fileprovider = pathconfig.getFileProviderIfPresent(fpkey);
				if (fileprovider == null) {
					if (localFilesProviderKey.equals(fpkey)) {
						fileprovider = localFiles;
					}
				}
				ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = getContentHandleCollection(fpkey);
				readDependencies(fpkey, fileprovider, coll, reader);
			}
			taskResults = (BuildTaskResultDatabase) reader.readObject();
		} catch (NoSuchFileException | FileNotFoundException e) {
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void readDependencies(RootFileProviderKey providerkey, SakerFileProvider fileprovider,
			ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll, ContentReaderObjectInput reader)
			throws IOException, ClassNotFoundException {
		SakerPath relative = null;
		while (true) {
			SakerPath readpath = (SakerPath) reader.readObject();
			if (readpath == null) {
				break;
			}
			SakerPath path;
			if (readpath.isRelative()) {
				path = relative.resolve(readpath);
			} else {
				path = readpath;
			}
			relative = path.getParent();

			ContentDescriptor expecteddiskcontent;
			try {
				expecteddiskcontent = (ContentDescriptor) reader.readObject();
			} catch (ClassNotFoundException e) {
				continue;
			}
			ContentDescriptor content;
			try {
				content = (ContentDescriptor) reader.readObject();
			} catch (ClassNotFoundException e) {
				continue;
			}
			if (fileprovider == null) {
				continue;
			}
			ContentDescriptorSupplier currentcontentsupplier = getContentDescriptorSupplier(providerkey, path);
			ContentHandleImpl handle = new ContentHandleImpl(this, content, expecteddiskcontent,
					SakerPathFiles.getPathKey(fileprovider, path), currentcontentsupplier);
			coll.put(path, handle);
		}
	}

	public Set<RootFileProviderKey> getTrackedFileProviderKeys() {
		return new HashSet<>(providerKeyPathDependencies.keySet());
	}

	public NavigableSet<SakerPath> getTrackedFilePaths(RootFileProviderKey providerkey) {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (coll == null) {
			return Collections.emptyNavigableSet();
		}
		return new TreeSet<>(coll.navigableKeySet());
	}

	public void recheckContentChangesOffline(Map<RootFileProviderKey, NavigableSet<SakerPath>> pathstorecheck) {
		if (pathstorecheck.isEmpty()) {
			return;
		}
		for (Entry<RootFileProviderKey, NavigableSet<SakerPath>> entry : pathstorecheck.entrySet()) {
			NavigableSet<SakerPath> paths = entry.getValue();
			if (paths.isEmpty()) {
				continue;
			}
			ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(entry.getKey());
			if (coll == null) {
				continue;
			}
			ConcurrentNavigableMap<SakerPath, ContentHandleImpl> collsub = coll.subMap(paths.first(), true,
					paths.last(), true);
			if (collsub.isEmpty()) {
				continue;
			}
			ObjectUtils.iterateOrderedIterables(paths, collsub.entrySet(), PATH_ENTRYKEY_PATH_COMPARATOR, (l, r) -> {
				if (r != null && l != null) {
					ContentHandleImpl handle = r.getValue();
					handle.invalidateOffline();
				}
			});
		}
	}

	public void invalidateEntryOffline(RootFileProviderKey providerkey, SakerPath filepath, BasicFileAttributes attrs) {
		if (!trackHandleAttributes) {
			throw new IllegalStateException("Handle changes are not tracked.");
		}
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (coll == null) {
			return;
		}
		if (!attrs.isDirectory()) {
			ContentHandleImpl handle = coll.get(filepath);
			if (handle == null) {
				//invalidate any sub files as the file is not a directory
				invalidateDirectoryChildrenOfflineImpl(coll, filepath);
				return;
			}
			BasicFileAttributes diskattrs = handle.diskAttributes;
			if (diskattrs != null) {
				if (!FileAttributesContentDescriptor.isChanged(attrs, diskattrs)) {
					//not changed
					return;
				}
			}
			//invalidate any sub files as the file is not a directory
			invalidateFileAndDirectoryChildrenOfflineImpl(coll, filepath);
			handle.setDiskAttributesOffline(attrs);
		} else {
			//the file is a directory
			ContentHandleImpl handle = coll.get(filepath);
			if (handle == null) {
				return;
			}
			//we got a content handle for the given file, however it is a directory
			//invalidate only the file but not the children
			handle.invalidateOffline();
		}
	}

	public void invalidateEntriesOffline(RootFileProviderKey providerkey, SakerPath dirpath,
			SortedMap<? extends String, ? extends BasicFileAttributes> entryattrs) {
		if (!trackHandleAttributes) {
			throw new IllegalStateException("Handle changes are not tracked.");
		}
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (coll == null) {
			return;
		}
		NavigableMap<SakerPath, ContentHandleImpl> dirsubentries = SakerPathFiles.getPathSubMapDirectoryChildren(coll,
				dirpath, false);
		if (dirsubentries.isEmpty()) {
			return;
		}
		int dirpathnc = dirpath.getNameCount();
		for (Entry<SakerPath, ContentHandleImpl> entry : dirsubentries.entrySet()) {
			SakerPath path = entry.getKey();
			String fname = path.getName(dirpathnc);
			if (path.getNameCount() == dirpathnc + 1) {
				//we are examining a direct child handle
				//   e.g. dir/path/fname
				BasicFileAttributes attrs = entryattrs.get(fname);
				ContentHandleImpl handle = entry.getValue();
				if (attrs != null) {
					BasicFileAttributes diskattrs = handle.diskAttributes;
					if (diskattrs != null) {
						if (!FileAttributesContentDescriptor.isChanged(attrs, diskattrs)) {
							//not changed
							continue;
						}
					}
				}
				invalidateFileAndDirectoryChildrenOfflineImpl(coll, path);
				handle.setDiskAttributesOffline(attrs);
			} else {
				//we are examining a sub child handle
				//   e.g. dir/path/fname/someotherfile
				BasicFileAttributes attrs = entryattrs.get(fname);
				if (attrs == null || !attrs.isDirectory()) {
					//the file can be invalidated as its parent have been modified
					invalidateFileAndDirectoryChildrenOfflineImpl(coll, path);
				}
			}
		}
	}

	public void invalidateAllOffline() {
		for (ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll : providerKeyPathDependencies.values()) {
			for (ContentHandleImpl handle : coll.values()) {
				handle.invalidateOffline();
			}
		}
	}

	private static void invalidateImpl(ConcurrentNavigableMap<SakerPath, ContentHandleImpl> coll, SakerPath path) {
		NavigableMap<SakerPath, ContentHandleImpl> dirandchildren = SakerPathFiles.getPathSubMapDirectoryChildren(coll,
				path, true);
		if (dirandchildren.isEmpty()) {
			return;
		}
		for (ContentHandleImpl handle : dirandchildren.values()) {
			handle.invalidate();
		}
	}

	private static void invalidateFileAndDirectoryChildrenOfflineImpl(
			ConcurrentNavigableMap<SakerPath, ContentHandleImpl> coll, SakerPath path) {
		NavigableMap<SakerPath, ContentHandleImpl> dirandchildren = SakerPathFiles.getPathSubMapDirectoryChildren(coll,
				path, true);
		if (dirandchildren.isEmpty()) {
			return;
		}
		for (ContentHandleImpl handle : dirandchildren.values()) {
			handle.invalidateOffline();
		}
	}

	private static void invalidateHardFileAndDirectoryChildrenOfflineImpl(
			ConcurrentNavigableMap<SakerPath, ContentHandleImpl> coll, SakerPath path) {
		NavigableMap<SakerPath, ContentHandleImpl> dirandchildren = SakerPathFiles.getPathSubMapDirectoryChildren(coll,
				path, true);
		if (dirandchildren.isEmpty()) {
			return;
		}
		for (ContentHandleImpl handle : dirandchildren.values()) {
			handle.invalidateHardOffline();
		}
	}

	private static void invalidateDirectoryChildrenOfflineImpl(
			ConcurrentNavigableMap<SakerPath, ContentHandleImpl> coll, SakerPath path) {
		NavigableMap<SakerPath, ContentHandleImpl> dirandchildren = SakerPathFiles.getPathSubMapDirectoryChildren(coll,
				path, false);
		if (dirandchildren.isEmpty()) {
			return;
		}
		for (ContentHandleImpl handle : dirandchildren.values()) {
			handle.invalidateOffline();
		}
	}

	@Override
	public void invalidate(PathKey pathkey) {
		Objects.requireNonNull(pathkey, "path key");
		this.invalidate(pathkey.getFileProviderKey(), pathkey.getPath());
	}

	@Override
	public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey) {
		Objects.requireNonNull(pathkey, "path key");
		RootFileProviderKey providerkey = pathkey.getFileProviderKey();
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = getContentHandleCollection(providerkey);
		SakerPath path = pathkey.getPath();
		ConcurrentNavigableMap<SakerPath, ContentHandleImpl> dirandchildren = SakerPathFiles
				.getPathSubMapDirectoryChildren(coll, path, true);
		for (ContentHandleImpl handle : dirandchildren.values()) {
			handle.invalidate();
		}
		ContentHandleImpl handle = getContentHandleFromCollection(pathkey, providerkey, path, dirandchildren);
		return handle.getContent();
	}

	private void invalidateSingle(RootFileProviderKey providerkey, SakerPath path) {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (ObjectUtils.isNullOrEmpty(coll)) {
			return;
		}
		ContentHandleImpl handle = coll.get(path);
		if (handle == null) {
			return;
		}
		handle.invalidate();
	}

	public void invalidate(RootFileProviderKey providerkey, SakerPath path) {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (ObjectUtils.isNullOrEmpty(coll)) {
			return;
		}
		invalidateImpl(coll, path);
	}

	public void invalidateOffline(RootFileProviderKey providerkey, SakerPath path) {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (ObjectUtils.isNullOrEmpty(coll)) {
			return;
		}
		invalidateFileAndDirectoryChildrenOfflineImpl(coll, path);
	}

	public void invalidateHardOffline(RootFileProviderKey providerkey, SakerPath path) {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
		if (ObjectUtils.isNullOrEmpty(coll)) {
			return;
		}
		invalidateHardFileAndDirectoryChildrenOfflineImpl(coll, path);
	}

	@Override
	public ContentHandle discover(ProviderHolderPathKey pathkey, ContentDescriptor content) throws IOException {
		RootFileProviderKey fpkey = pathkey.getFileProviderKey();
		SakerPath path = pathkey.getPath();
		SakerFileProvider fp = pathkey.getFileProvider();
		ContentHandleImpl handle = getContentHandleImpl(pathkey);
		synchronized (getPathLock(fpkey, path)) {
			ContentDescriptorSupplier contentsupplier = handle.contentSupplier;
			ContentDescriptor diskcontent;
			BasicFileAttributes diskattributes;
			if (trackHandleAttributes) {
				diskattributes = fp.getFileAttributes(path);
				diskcontent = contentsupplier.getUsingFileAttributes(pathkey, diskattributes);
			} else {
				diskattributes = null;
				diskcontent = contentsupplier.get(pathkey);
			}
			if (DirectoryContentDescriptor.INSTANCE.equals(diskcontent)) {
				throw new InvalidFileTypeException("File at path: " + path + " is a directory.");
			}
			handle.setContent(content, diskcontent, diskattributes);
		}
		return handle;
	}

	@Override
	public synchronized void close() throws IOException {
		flush();
	}

	@Override
	public ContentHandleAttributes discoverFileAttributes(ProviderHolderPathKey pathkey) throws IOException {
		SakerPath filepath = pathkey.getPath();

		FileEntry attributes;
		ContentHandleImpl handle;
		attributes = pathkey.getFileProvider().getFileAttributes(filepath);
		if (attributes.isDirectory()) {
			handle = null;
		} else {
			handle = getContentHandleImpl(pathkey);
			FileEntry trackattributes = null;
			if (trackHandleAttributes) {
				trackattributes = attributes;
			}
			handle.discoverAttributes(attributes, trackattributes);
		}
		return new ContentHandleAttributes(handle, attributes);
	}

	@Override
	public NavigableMap<String, ContentHandleAttributes> discoverDirectoryChildrenAttributes(
			ProviderHolderPathKey directorypathkey) throws IOException {
		SakerFileProvider fp = directorypathkey.getFileProvider();
		SakerPath dirpath = directorypathkey.getPath();
		Map<String, ? extends FileEntry> entries = fp.getDirectoryEntries(dirpath);
		if (entries.isEmpty()) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<String, ContentHandleAttributes> result = new TreeMap<>();
		for (Entry<String, ? extends FileEntry> entry : entries.entrySet()) {
			String fname = entry.getKey();
			FileEntry attributes = entry.getValue();
			ContentHandleImpl handle;
			if (attributes.isDirectory()) {
				handle = null;
			} else {
				SakerPath filepath = dirpath.resolve(fname);
				SimpleProviderHolderPathKey pathkey = new SimpleProviderHolderPathKey(directorypathkey, filepath);
				handle = getContentHandleImpl(pathkey);
				FileEntry trackattributes = null;
				if (trackHandleAttributes) {
					trackattributes = attributes;
				}
				handle.discoverAttributes(attributes, trackattributes);
			}
			result.put(fname, new ContentHandleAttributes(handle, attributes));
		}
		return result;
	}

	private ConcurrentSkipListMap<SakerPath, ContentHandleImpl> getContentHandleCollection(
			RootFileProviderKey providerkey) {
		return providerKeyPathDependencies.computeIfAbsent(providerkey, Functionals.concurrentSkipListMapComputer());
	}

	@Override
	public ContentHandle getContentHandle(ProviderHolderPathKey pathkey) {
		return getContentHandleImpl(pathkey);
	}

	private ContentHandleImpl getContentHandleImpl(ProviderHolderPathKey pathkey) {
		RootFileProviderKey providerkey = pathkey.getFileProviderKey();
		SakerPath path = pathkey.getPath();
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = getContentHandleCollection(providerkey);
		return getContentHandleFromCollection(pathkey, providerkey, path, coll);
	}

	private ContentHandleImpl getContentHandleFromCollection(ProviderHolderPathKey pathkey,
			RootFileProviderKey providerkey, SakerPath path,
			ConcurrentNavigableMap<SakerPath, ContentHandleImpl> coll) {
		ContentHandleImpl handle = coll.computeIfAbsent(path, p -> {
			return new ContentHandleImpl(this, pathkey, getContentDescriptorSupplier(providerkey, p));
		});
		return handle;
	}

	@Override
	public ContentDescriptor getContentDescriptor(ProviderHolderPathKey pathkey) {
		return getContentHandle(pathkey).getContent();
	}

	public ContentDescriptor getContentDescriptorIfPresent(PathKey pathkey) {
		return getContentDescriptorIfPresent(pathkey.getFileProviderKey(), pathkey.getPath());
	}

	public ContentDescriptor getContentDescriptorIfPresent(RootFileProviderKey providerkey, SakerPath sakerpath) {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = getContentHandleCollection(providerkey);
		ContentHandleImpl handle = coll.get(sakerpath);
		if (handle != null) {
			return handle.getContent();
		}
		return null;
	}

	private Object getPathLock(RootFileProviderKey providerkey, SakerPath path) {
		ConcurrentSkipListMap<SakerPath, Object> coll = providerKeyPathUpdateLocks.computeIfAbsent(providerkey,
				Functionals.concurrentSkipListMapComputer());
		return coll.computeIfAbsent(path, Functionals.objectComputer());
	}

	private void deleteWithContent(RootFileProviderKey providerkey, SakerPath path, ContentDescriptor contentdescriptor,
			ContentUpdater updater) throws IOException {
		ConcurrentSkipListMap<SakerPath, ContentHandleImpl> deps = providerKeyPathDependencies.get(providerkey);
		synchronized (getPathLock(providerkey, path)) {
			if (deps == null) {
				return;
			}
			ContentHandleImpl handle = deps.get(path);
			if (handle == null) {
				return;
			}
			if (Objects.equals(handle.getContent(), contentdescriptor)) {
				handle.invalidate();
				checkWriteEnabled(providerkey, path);
				setDirty();
				updater.update();
			}
		}
	}

	@Override
	public DeferredSynchronizer synchronizeDeferred(ProviderHolderPathKey pathkey, ContentDescriptor content,
			ContentUpdater updater) {
		Objects.requireNonNull(content);

		return synchronizeDeferredImpl(pathkey, content, updater);
	}

	private DeferredSynchronizer synchronizeDeferredImpl(ProviderHolderPathKey pathkey, ContentDescriptor content,
			ContentUpdater updater) {
		ContentHandleImpl handle = getContentHandleImpl(pathkey);

		ContentDescriptor checkcontent = handle.getContent();
		if (!content.isChanged(checkcontent)) {
			return null;
		}

		return new DeferredSynchronizerImpl(handle, content, checkcontent, pathkey, updater);
	}

	@Override
	public ByteSource openInputWithContentOrSynchronize(ProviderHolderPathKey pathkey, ContentDescriptor content,
			ContentUpdater updater) throws IOException {
		return openInputStreamWithContentOrSynchroinzeImpl(pathkey.getFileProviderKey(), pathkey.getFileProvider(),
				pathkey.getPath(), content, updater, pathkey);
	}

	private ByteSource openInputStreamWithContentOrSynchroinzeImpl(RootFileProviderKey fpkey, SakerFileProvider fp,
			SakerPath path, ContentDescriptor content, ContentUpdater updater, ProviderHolderPathKey pathkey)
			throws IOException {
		Objects.requireNonNull(content);

		ContentHandleImpl handle = getContentHandleImpl(pathkey);
		synchronized (getPathLock(fpkey, path)) {
			ContentDescriptor pathcontent = handle.getContent();
			if (!content.isChanged(pathcontent)) {
				return fp.openInput(path);
			}
			executeSynchronizeLocked(fp, path, content, updater, handle, fpkey, pathkey);

			return fp.openInput(path);
		}
	}

	private void writeToStreamWithContentOrSynchronizeImpl(RootFileProviderKey fpkey, SakerFileProvider fp,
			SakerPath path, ContentDescriptor content, ByteSink os, ContentUpdater updater,
			ProviderHolderPathKey pathkey) throws IOException {
		Objects.requireNonNull(content);

		ContentHandleImpl handle = getContentHandleImpl(pathkey);
		synchronized (getPathLock(fpkey, path)) {
			ContentDescriptor pathcontent = handle.getContent();
			if (!content.isChanged(pathcontent)) {
				fp.writeTo(path, os);
				return;
			}
			checkWriteEnabled(fpkey, path);
			setDirty();

			ContentDescriptorSupplier contentsupplier = handle.contentSupplier;
			IOException secondaryioexc = null;
			try {
				ByteSink contentcalcoutput = contentsupplier.getCalculatingOutput();
				if (contentcalcoutput != null) {
					try {
						PriorityMultiplexOutputStream multiplexer = new PriorityMultiplexOutputStream(
								ByteSink.toOutputStream(contentcalcoutput), ByteSink.toOutputStream(os));
						boolean calculated;
						try {
							calculated = updater.updateWithStream(multiplexer);
						} catch (SecondaryStreamException e) {
							secondaryioexc = e.getCause();
							calculated = false;
						}
						if (!calculated) {
							fp.writeTo(path, multiplexer);
						}
						ContentDescriptor diskcontent = contentsupplier.getCalculatedOutput(pathkey, contentcalcoutput);
						handle.setContent(content, diskcontent, getFileAttributesIfTracked(fp, path));
						IOUtils.throwExc(IOUtils.addExc(secondaryioexc, multiplexer.getSecondaryException()));
					} finally {
						contentcalcoutput.close();
					}
				} else {
					boolean streamed;
					try {
						streamed = updater.updateWithStream(os);
					} catch (SecondaryStreamException e) {
						secondaryioexc = e.getCause();
						//don't try to write to the stream again, set streamed to true
						streamed = true;
					}
					ContentDescriptor diskcontent;
					BasicFileAttributes diskattributes = null;
					if (trackHandleAttributes) {
						diskattributes = fp.getFileAttributes(path);
						diskcontent = contentsupplier.getUsingFileAttributes(pathkey, diskattributes);
					} else {
						diskcontent = contentsupplier.get(pathkey);
					}
					handle.setContent(content, diskcontent, diskattributes);
					if (!streamed) {
						fp.writeTo(path, os);
					}
				}
			} catch (IOException e) {
				handle.invalidate();
				IOUtils.addExc(e, secondaryioexc);
				throw e;
			}
			IOUtils.throwExc(secondaryioexc);
		}
	}

	@Override
	public void writeToStreamWithContentOrSynchronize(ProviderHolderPathKey pathkey, ContentDescriptor content,
			ByteSink os, ContentUpdater updater) throws IOException {
		writeToStreamWithContentOrSynchronizeImpl(pathkey.getFileProviderKey(), pathkey.getFileProvider(),
				pathkey.getPath(), content, os, updater, pathkey);
	}

	//doc: returns negative if the contents doesn't equal, else the written byte count
	public long writeToStreamWithExactContent(ProviderHolderPathKey pathkey, ContentDescriptor content, ByteSink os)
			throws IOException {
		RootFileProviderKey fpkey = pathkey.getFileProviderKey();
		SakerPath path = pathkey.getPath();
		ContentHandleImpl handle = getContentHandleImpl(pathkey);
		synchronized (getPathLock(fpkey, path)) {
			if (!Objects.equals(handle.getContent(), content)) {
				return -1;
			}
			return pathkey.getFileProvider().writeTo(path, os);
		}
	}

	@Override
	public ByteArrayRegion getBytesWithContentOrSynchronize(ProviderHolderPathKey pathkey, ContentDescriptor content,
			ContentUpdater updater) throws IOException {
		return getBytesWithContentOrSynchronizeImpl(pathkey.getFileProviderKey(), pathkey.getFileProvider(),
				pathkey.getPath(), content, updater, pathkey);
	}

	private ByteArrayRegion getBytesWithContentOrSynchronizeImpl(RootFileProviderKey fpkey, SakerFileProvider fp,
			SakerPath path, ContentDescriptor content, ContentUpdater updater, ProviderHolderPathKey pathkey)
			throws IOException {
		Objects.requireNonNull(content);

		ContentHandleImpl handle = getContentHandleImpl(pathkey);
		synchronized (getPathLock(fpkey, path)) {
			if (!content.isChanged(handle.getContent())) {
				return fp.getAllBytes(path);
			}

			checkWriteEnabled(fpkey, path);
			setDirty();

			ContentDescriptorSupplier contentsupplier = handle.contentSupplier;
			try {
				ByteSink contentcalcoutput = contentsupplier.getCalculatingOutput();
				if (contentcalcoutput != null) {
					try {
						UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
						MultiplexOutputStream multiplexer = new MultiplexOutputStream(
								ByteSink.toOutputStream(contentcalcoutput), baos);
						boolean calculated;
						try {
							calculated = updater.updateWithStream(multiplexer);
						} catch (SecondaryStreamException e) {
							//secondary exception happens when the content calculating output throws
							//ignoreable by setting the calculated to false
							calculated = false;
						}
						if (calculated) {
							handle.setContent(content, contentsupplier.getCalculatedOutput(pathkey, contentcalcoutput),
									getFileAttributesIfTracked(fp, path));
							return baos.toByteArrayRegion();
						}
						ByteArrayRegion result = fp.getAllBytes(path);
						BasicFileAttributes trackedattrs = getFileAttributesIfTracked(fp, path);
						handle.setContent(content, contentsupplier.getUsingFileContent(pathkey, result, trackedattrs),
								trackedattrs);
						return result;
					} finally {
						contentcalcoutput.close();
					}
				}
				UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
				boolean streamed;
				try {
					streamed = updater.updateWithStream(baos);
				} catch (SecondaryStreamException e) {
					//secondary exception should never happen, as we're writing to a byte array, ignoreable
					streamed = false;
				}
				ByteArrayRegion result;
				if (streamed) {
					result = baos.toByteArrayRegion();
				} else {
					result = fp.getAllBytes(path);
				}
				BasicFileAttributes trackedattrs = getFileAttributesIfTracked(fp, path);
				ContentDescriptor diskcontent = contentsupplier.getUsingFileContent(pathkey, result, trackedattrs);
				handle.setContent(content, diskcontent, trackedattrs);
				return result;
			} catch (IOException e) {
				handle.invalidate();
				throw e;
			}
		}
	}

	private BasicFileAttributes getFileAttributesIfTracked(SakerFileProvider fp, SakerPath path)
			throws IOException, FileNotFoundException {
		return trackHandleAttributes ? fp.getFileAttributes(path) : null;
	}

	@Override
	public void synchronize(ProviderHolderPathKey pathkey, ContentDescriptor content, ContentUpdater updater)
			throws IOException {
		synchronizeImpl(pathkey.getFileProviderKey(), pathkey.getFileProvider(), pathkey.getPath(), content, updater,
				pathkey);
	}

	private void synchronizeImpl(RootFileProviderKey fpkey, SakerFileProvider fp, SakerPath path,
			ContentDescriptor content, ContentUpdater updater, ProviderHolderPathKey pathkey) throws IOException {
		Objects.requireNonNull(content);

		ContentHandleImpl handle = getContentHandleImpl(pathkey);

		synchronized (getPathLock(fpkey, path)) {
			if (content.isChanged(handle.getContent())) {
				executeSynchronizeLocked(fp, path, content, updater, handle, fpkey, pathkey);
			}
		}
	}

	//XXX clean up the parameters of the private methods. (path, file provider, provider key parameters to be removed, and pathkey to be used)

	private void executeSynchronizeLocked(SakerFileProvider fp, SakerPath path, ContentDescriptor content,
			ContentUpdater updater, ContentHandleImpl handle, RootFileProviderKey providerkey,
			ProviderHolderPathKey pathkey) throws IOException {
		checkWriteEnabled(providerkey, path);

		setDirty();
		ContentDescriptorSupplier contentsupplier = handle.contentSupplier;
		IOException secondaryioexc = null;
		try {
			ByteSink contentcalcoutput = contentsupplier.getCalculatingOutput();
			if (contentcalcoutput != null) {
				try {
					boolean calculated;
					try {
						calculated = updater.updateWithStream(contentcalcoutput);
					} catch (SecondaryStreamException e) {
						secondaryioexc = e.getCause();
						calculated = false;
					}
					ContentDescriptor diskcontent;
					BasicFileAttributes diskattributes = null;
					if (calculated) {
						diskcontent = contentsupplier.getCalculatedOutput(pathkey, contentcalcoutput);
					} else {
						if (trackHandleAttributes) {
							diskattributes = fp.getFileAttributes(path);
							diskcontent = contentsupplier.getUsingFileAttributes(pathkey, diskattributes);
						} else {
							diskcontent = contentsupplier.get(pathkey);
						}
					}
					handle.setContent(content, diskcontent, diskattributes);
				} finally {
					contentcalcoutput.close();
				}
			} else {
				updater.update();
				ContentDescriptor diskcontent;
				BasicFileAttributes diskattributes = null;
				if (trackHandleAttributes) {
					diskattributes = fp.getFileAttributes(path);
					diskcontent = contentsupplier.getUsingFileAttributes(pathkey, diskattributes);
				} else {
					diskcontent = contentsupplier.get(pathkey);
				}
				handle.setContent(content, diskcontent, diskattributes);
			}
		} catch (IOException e) {
			handle.invalidate();
			IOUtils.addExc(e, secondaryioexc);
			throw e;
		}
		IOUtils.throwExc(secondaryioexc);
	}

	public void clean() {
		setDirty();
		providerKeyPathDependencies.clear();
		taskResults.clear();
		synchronized (this) {
			if (!isPersisting()) {
				return;
			}
			try {
				descriptorsFileProvider.delete(descriptorsFilePath);
				this.dirty = false;
			} catch (IOException e) {
				// XXX handle exception somehow?
				e.printStackTrace();
			}
		}
	}

	@Override
	public void createDirectoryAtPath(ProviderHolderPathKey pathkey) throws IOException {
		SakerPath path = pathkey.getPath();
		RootFileProviderKey providerkey = pathkey.getFileProviderKey();
		checkWriteEnabled(providerkey, path);

		synchronized (getPathLock(providerkey, path)) {
			SakerFileProvider fileprovider = pathkey.getFileProvider();
			int writeres = fileprovider.ensureWriteRequest(path, FileEntry.TYPE_DIRECTORY,
					SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);
			if (((writeres
					& SakerFileProvider.RESULT_FLAG_FILES_DELETED) == SakerFileProvider.RESULT_FLAG_FILES_DELETED)) {
				//if there was a file deleted, then invalidate the handle
				//in any other cases, sub handles are already considered to be part of the directory, and need no invalidation
				invalidateSingle(providerkey, path);
			}
		}
	}

	@Override
	public void syncronizeDirectory(ProviderHolderPathKey pathkey, IORunnable dirsynchronizer) throws IOException {
		SakerPath path = pathkey.getPath();
		RootFileProviderKey providerkey = pathkey.getFileProviderKey();
		checkWriteEnabled(providerkey, path);

		synchronized (getPathLock(providerkey, path)) {
			SakerFileProvider fileprovider = pathkey.getFileProvider();
			int writeres = fileprovider.ensureWriteRequest(path, FileEntry.TYPE_DIRECTORY,
					SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);
			if (((writeres
					& SakerFileProvider.RESULT_FLAG_FILES_DELETED) == SakerFileProvider.RESULT_FLAG_FILES_DELETED)) {
				//if there was a file deleted, then invalidate the handle
				//in any other cases, sub handles are already considered to be part of the directory, and need no invalidation
				invalidateSingle(providerkey, path);
			}
			dirsynchronizer.run();
		}
	}

	@Override
	public void deleteChildrenIfNotIn(ProviderHolderPathKey pathkey, Set<String> keepchildren) throws IOException {
		SakerFileProvider fileprovider = pathkey.getFileProvider();
		SakerPath path = pathkey.getPath();
		RootFileProviderKey providerkey = pathkey.getFileProviderKey();
		checkWriteEnabled(providerkey, path);

		Set<String> deletedchildren = fileprovider.deleteChildrenRecursivelyIfNotIn(path, keepchildren);
		if (!deletedchildren.isEmpty()) {
			ConcurrentSkipListMap<SakerPath, ContentHandleImpl> coll = providerKeyPathDependencies.get(providerkey);
			if (coll == null) {
				return;
			}
			//TODO this invalidates all children in the directory. we should only invalidate the deleted ones
			invalidateImpl(coll, path);
		}
	}

	private void checkWriteEnabled(RootFileProviderKey providerkey, SakerPath path) {
		PathProtectionSettings protsettings = protectionSettings;
		if (protsettings == null) {
			//all writes are enabled
			return;
		}
		NavigableSet<SakerPath> enableddirs = protsettings.writeEnabledDirectories.get(providerkey);
		if (SakerPathFiles.hasPathOrParent(enableddirs, path)) {
			//writing is enabled in the directory
			return;
		}
		StringBuilder messagesb = new StringBuilder();
		messagesb.append("Write request on ");
		SakerPath execpath = pathConfiguration.toExecutionPath(new SimplePathKey(path, providerkey));
		if (execpath != null) {
			messagesb.append(execpath);
		} else {
			messagesb.append(providerkey.getUUID());
			messagesb.append(" with path: ");
			messagesb.append(path);
		}
		int res = protsettings.prompter.prompt("File write request", messagesb.toString(),
				ImmutableUtils.asUnmodifiableArrayList("Allow", "Forbid"));
		if (res != 0) {
			throw new SecurityException("Write request forbidden on: " + path);
		}
	}
}
