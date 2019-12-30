package saker.build.file.provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * File provider implementation which acts as a delegate to a subject file provider.
 * <p>
 * This implementation is able to <i>mount</i> a directory of the subject file provider and expose it as a root. This
 * file provider will forward calls to the subject by resolving the argument paths against the mounted directory path.
 * <p>
 * Use the static factory methods to create an instance. (See {@link #create})
 */
@RMIWrap(DirectoryMountFileProvider.ProviderRMIWrapper.class)
public class DirectoryMountFileProvider implements SakerFileProvider {
	//TODO implement a restricting file provider, that only allows accessing file under a directory. unwrapped file providers should have an instance of that

	private final SakerFileProvider subject;
	private final SakerPath directoryPath;
	private final String rootName;

	private final transient DirectoryMountProviderKey providerKey;
	private final transient Set<String> roots;

	private DirectoryMountFileProvider(SakerFileProvider subject, SakerPath directoryPath, String rootName) {
		this.subject = subject;
		this.directoryPath = directoryPath;
		this.rootName = rootName;
		this.providerKey = new DirectoryMountProviderKey(subject.getProviderKey(), rootName, directoryPath);

		this.roots = ImmutableUtils.singletonSet(rootName);
	}

	public static SakerFileProvider create(ProviderHolderPathKey pathkey, String rootName) throws NullPointerException {
		Objects.requireNonNull(pathkey, "path key");
		return create(pathkey.getFileProvider(), pathkey.getPath(), rootName);
	}

	public static SakerFileProvider create(SakerFileProvider subject, SakerPath directoryPath, String rootName)
			throws NullPointerException {
		Objects.requireNonNull(rootName, "rootname");
		Objects.requireNonNull(subject, "subject");
		Objects.requireNonNull(directoryPath, "directorypath");

		rootName = SakerPath.normalizeRoot(rootName);

		if (directoryPath.getNameCount() == 0 && directoryPath.getRoot().equals(rootName)) {
			//nothing to mount
			return subject;
		}
		return new DirectoryMountFileProvider(subject, directoryPath, rootName);
	}

	public String getRootName() {
		return rootName;
	}

	public SakerPath getDirectoryPath() {
		return directoryPath;
	}

	@Override
	public SakerFileProvider getWrappedProvider() {
		return subject;
	}

	@Override
	public SakerPath resolveWrappedPath(SakerPath path) {
		if (!rootName.equals(path.getRoot())) {
			throw new IllegalArgumentException("Invalid root: " + path.getRoot() + " expected: " + rootName);
		}
		return directoryPath.resolve(path.forcedRelative());
	}

	@Override
	public Set<String> getRoots() throws IOException {
		return roots;
	}

	@Override
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(SakerPath path) throws IOException {
		return subject.getDirectoryEntries(toSubjectPath(path));
	}

	@Override
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(SakerPath path)
			throws IOException {
		return subject.getDirectoryEntriesRecursively(toSubjectPath(path));
	}

	@Override
	public Set<String> getDirectoryEntryNames(SakerPath path) throws IOException {
		return subject.getDirectoryEntryNames(toSubjectPath(path));
	}

	@Override
	public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException, FileNotFoundException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Cannot open input for: " + path);
		}
		return subject.openInput(toSubjectPath(path), openoptions);
	}

	@Override
	public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Cannot open output for: " + path);
		}
		return subject.openOutput(toSubjectPath(path), openoptions);
	}

	@Override
	public int ensureWriteRequest(SakerPath path, int filetype, int opflag) throws IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Path is read-only: " + path);
		}
		return subject.ensureWriteRequest(toSubjectPath(path), filetype, opflag);
	}

	@Override
	public FileEntry getFileAttributes(SakerPath path, LinkOption... linkoptions)
			throws IOException, FileNotFoundException {
		return subject.getFileAttributes(toSubjectPath(path), linkoptions);
	}

	@Override
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException {
		subject.setLastModifiedMillis(toSubjectPath(path), millis);
	}

	@Override
	public void createDirectories(SakerPath path) throws IOException {
		subject.createDirectories(toSubjectPath(path));
	}

	@Override
	public void deleteRecursively(SakerPath path) throws IOException {
		subject.deleteRecursively(toSubjectPath(path));
	}

	@Override
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException {
		subject.delete(toSubjectPath(path));
	}

	@Override
	public void clearDirectoryRecursively(SakerPath path) throws IOException {
		subject.clearDirectoryRecursively(toSubjectPath(path));
	}

	@Override
	public int deleteRecursivelyIfNotFileType(SakerPath path, int filetype) throws IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Path is read-only: " + path);
		}
		return subject.deleteRecursivelyIfNotFileType(toSubjectPath(path), filetype);
	}

	@Override
	public Set<String> getSubDirectoryNames(SakerPath path) throws IOException {
		return subject.getSubDirectoryNames(toSubjectPath(path));
	}

	@Override
	public ByteArrayRegion getAllBytes(SakerPath path, OpenOption... openoptions) throws IOException {
		return subject.getAllBytes(toSubjectPath(path), openoptions);
	}

	@Override
	public Set<String> deleteChildrenRecursivelyIfNotIn(SakerPath path, Set<String> childfilenames) throws IOException {
		return subject.deleteChildrenRecursivelyIfNotIn(toSubjectPath(path), childfilenames);
	}

	@Override
	public FileEventListener.ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener)
			throws IOException {
		return subject.addFileEventListener(toSubjectPath(directory), listener);
	}

	@Override
	public FileProviderKey getProviderKey() {
		return providerKey;
	}

	@Override
	public SakerFileLock createLockFile(SakerPath path) throws IOException {
		return subject.createLockFile(toSubjectPath(path));
	}

	@Override
	public boolean isChanged(SakerPath path, long size, long modificationmillis, LinkOption... linkoptions) {
		if (!rootName.equals(path.getRoot())) {
			return true;
		}
		return subject.isChanged(directoryPath.resolve(path.forcedRelative()), size, modificationmillis, linkoptions);
	}

	@Override
	public long writeTo(SakerPath path, ByteSink out, OpenOption... openoptions) throws IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Cannot open input for: " + path);
		}
		return subject.writeTo(toSubjectPath(path), out, openoptions);
	}

	@Override
	public void moveFile(SakerPath source, SakerPath target, CopyOption... copyoptions) throws IOException {
		if (source.getNameCount() == 0) {
			throw new AccessDeniedException(source.toString(), target.toString(), "Path is read-only: " + source);
		}
		if (target.getNameCount() == 0) {
			throw new AccessDeniedException(target.toString(), target.toString(), "Path is read-only: " + target);
		}
		subject.moveFile(toSubjectPath(source), toSubjectPath(target), copyoptions);
	}

	@Override
	public void setFileBytes(SakerPath path, ByteArrayRegion data, OpenOption... openoptions) throws IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Path is read-only: " + path);
		}
		subject.setFileBytes(toSubjectPath(path), data, openoptions);
	}

	@Override
	public long writeToFile(ByteSource is, SakerPath path, OpenOption... openoptions) throws IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Path is read-only: " + path);
		}
		return subject.writeToFile(is, toSubjectPath(path), openoptions);
	}

	@Override
	public void removeFileEventListeners(Iterable<? extends FileEventListener.ListenerToken> listeners) {
		subject.removeFileEventListeners(listeners);
	}

	@Override
	public FileHashResult hash(SakerPath path, String algorithm, OpenOption... openoptions)
			throws NoSuchAlgorithmException, IOException {
		if (path.getNameCount() == 0) {
			throw new AccessDeniedException(path.toString(), null, "Cannot open input for: " + path);
		}
		return subject.hash(toSubjectPath(path), algorithm, openoptions);
	}

	@Override
	public Entry<String, ? extends FileEntry> getDirectoryEntryIfSingle(SakerPath path) throws IOException {
		return subject.getDirectoryEntryIfSingle(toSubjectPath(path));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (directoryPath != null ? "directoryPath=" + directoryPath + ", " : "")
				+ (rootName != null ? "rootName=" + rootName : "") + "]";
	}

	private SakerPath toSubjectPath(SakerPath path) throws NoSuchFileException {
		if (!rootName.equals(path.getRoot())) {
			throw new NoSuchFileException(path.toString(), null,
					"Invalid root: " + path.getRoot() + " expected: " + rootName);
		}
		return directoryPath.resolve(path.forcedRelative());
	}

	protected static final class ProviderRMIWrapper implements RMIWrapper {
		private DirectoryMountFileProvider fp;

		public ProviderRMIWrapper() {
		}

		public ProviderRMIWrapper(DirectoryMountFileProvider fp) {
			this.fp = fp;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(fp.subject);
			out.writeObject(fp.directoryPath);
			out.writeUTF(fp.rootName);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			SakerFileProvider subject = (SakerFileProvider) in.readObject();
			SakerPath dirpath = (SakerPath) in.readObject();
			String rootname = in.readUTF();
			fp = new DirectoryMountFileProvider(subject, dirpath, rootname);
		}

		@Override
		public Object resolveWrapped() {
			return fp;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}
}
