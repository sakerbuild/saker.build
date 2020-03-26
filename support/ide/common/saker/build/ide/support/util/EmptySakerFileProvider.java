package saker.build.ide.support.util;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileEventListener;
import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.file.provider.FileProviderKey;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;

public class EmptySakerFileProvider implements SakerFileProvider {
	private static final class EmptyProviderRootFileProviderKey implements RootFileProviderKey {
		private UUID uuid;

		public EmptyProviderRootFileProviderKey(UUID uuid) {
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
			EmptyProviderRootFileProviderKey other = (EmptyProviderRootFileProviderKey) obj;
			if (uuid == null) {
				if (other.uuid != null)
					return false;
			} else if (!uuid.equals(other.uuid))
				return false;
			return true;
		}
	}

	private final Set<String> roots;
	private final EmptyProviderRootFileProviderKey fileProviderKey;

	public EmptySakerFileProvider(Set<String> roots) {
		this.roots = roots;
		this.fileProviderKey = new EmptyProviderRootFileProviderKey(
				UUID.nameUUIDFromBytes(ImmutableUtils.makeImmutableNavigableSet(roots).toString().getBytes()));
	}

	@Override
	public Set<String> getRoots() throws IOException {
		return roots;
	}

	@Override
	public FileProviderKey getProviderKey() {
		return fileProviderKey;
	}

	@Override
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(SakerPath path) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(SakerPath path)
			throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public FileEntry getFileAttributes(SakerPath path, LinkOption... linkoptions) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public void createDirectories(SakerPath path) throws IOException, FileAlreadyExistsException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public void deleteRecursively(SakerPath path) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public int ensureWriteRequest(SakerPath path, int filetype, int operationflag)
			throws IOException, IllegalArgumentException {
		throw new NoSuchFileException(Objects.requireNonNull(path, "path").toString());
	}

	@Override
	public void moveFile(SakerPath source, SakerPath target, CopyOption... copyoptions) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(source, "source").toString());
	}

	@Override
	public ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener) throws IOException {
		throw new NoSuchFileException(Objects.requireNonNull(directory, "directory").toString());
	}

}
