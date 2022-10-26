package saker.build.ide.support.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileEventListener;
import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.file.provider.FileProviderKey;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;

public class EmptySakerFileProvider implements SakerFileProvider {
	private static final class EmptyProviderRootFileProviderKey implements RootFileProviderKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private UUID uuid;

		/**
		 * For {@link Externalizable}.
		 */
		public EmptyProviderRootFileProviderKey() {
		}

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

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append('[');
			builder.append(uuid);
			builder.append(']');
			return builder.toString();
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

	private final NavigableSet<String> roots;
	private final EmptyProviderRootFileProviderKey fileProviderKey;

	public EmptySakerFileProvider(NavigableSet<String> roots) {
		this.roots = roots;
		this.fileProviderKey = new EmptyProviderRootFileProviderKey(
				UUID.nameUUIDFromBytes(StringUtils.toStringJoin(";", roots).getBytes(StandardCharsets.UTF_8)));
	}

	@Override
	public NavigableSet<String> getRoots() throws IOException {
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
