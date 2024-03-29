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
package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.path.PathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.util.data.annotation.ValueType;

/**
 * {@link ContentDescriptor} implementation that is based on the attributes of a file at a given location.
 * <p>
 * This content descriptor stores the last modification time and size of the file at a given location. The location is
 * defined by the path to the file, and the {@linkplain RootFileProviderKey root file provider}.
 * <p>
 * The content descriptors are considered to be changed if any of the above attriutes change.
 * <p>
 * Use the static factory methods to create a new instance with the given attributes.
 * <p>
 * This content descriptor is designed to represent regular files. The static factory methods will return the
 * {@link DirectoryContentDescriptor} singleton if an attributes of a directory is passed as an argument for the
 * instantiation. This is due to the fact that directories are to be treated as basic containers for files that contain
 * the contents.
 */
@PublicApi
@ValueType
public final class FileAttributesContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private RootFileProviderKey providerKey;
	private SakerPath filePath;
	private long lastModifiedMillis;
	private long size;

	/**
	 * For {@link Externalizable}.
	 */
	public FileAttributesContentDescriptor() {
	}

	private FileAttributesContentDescriptor(RootFileProviderKey providerkey, SakerPath path,
			BasicFileAttributes attrs) {
		this(providerkey, path, attrs.lastModifiedTime().toMillis(), attrs.size());
	}

	private FileAttributesContentDescriptor(RootFileProviderKey providerkey, SakerPath path, FileEntry entry) {
		this(providerkey, path, entry.getLastModifiedMillis(), entry.getSize());
	}

	private FileAttributesContentDescriptor(RootFileProviderKey providerKey, SakerPath filePath,
			long lastModifiedMillis, long size) {
		this.providerKey = providerKey;
		this.filePath = filePath;
		this.lastModifiedMillis = lastModifiedMillis;
		this.size = size;
	}

	/**
	 * Creates a new content descriptor for the given attributes.
	 * 
	 * @param pathkey
	 *            The path key of the associated file.
	 * @param attrs
	 *            The attributes of the file.
	 * @return A content descriptor for the given arguments.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static ContentDescriptor create(PathKey pathkey, BasicFileAttributes attrs) throws NullPointerException {
		Objects.requireNonNull(attrs, "attrs");
		if (attrs.isDirectory()) {
			return DirectoryContentDescriptor.INSTANCE;
		}
		Objects.requireNonNull(pathkey, "path key");
		return new FileAttributesContentDescriptor(pathkey.getFileProviderKey(), pathkey.getPath(), attrs);
	}

	/**
	 * Creates a new content descriptor for the given attributes.
	 * 
	 * @param pathkey
	 *            The path key of the associated file.
	 * @param attrs
	 *            The attributes of the file.
	 * @return A content descriptor for the given arguments.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static ContentDescriptor create(PathKey pathkey, FileEntry attrs) throws NullPointerException {
		Objects.requireNonNull(attrs, "attrs");
		if (attrs.isDirectory()) {
			return DirectoryContentDescriptor.INSTANCE;
		}
		Objects.requireNonNull(pathkey, "path key");
		return new FileAttributesContentDescriptor(pathkey.getFileProviderKey(), pathkey.getPath(), attrs);
	}

	/**
	 * Determines if the argument attributes should be considered changed defined by the rules of this class.
	 * <p>
	 * This method will check if the argument attributes have the same size and last modification time.
	 * <p>
	 * If any of the arguments represent a directory, <code>true</code> is returned if and only if the other argument is
	 * not a directory.
	 * 
	 * @param current
	 *            The current attributes.
	 * @param prev
	 *            The previous attributes.
	 * @return <code>true</code> if the attributes changed.
	 * @throws NullPointerException
	 *             If any if the arguments are <code>null</code>.
	 */
	public static boolean isChanged(BasicFileAttributes current, BasicFileAttributes prev) throws NullPointerException {
		Objects.requireNonNull(current, "current attrs");
		Objects.requireNonNull(prev, "previous attrs");
		if (current.isDirectory()) {
			return !prev.isDirectory();
		}
		if (prev.isDirectory()) {
			//current is not a directory
			return true;
		}
		if (current.size() != prev.size()) {
			return true;
		}
		//compare the millis directly, as using compareTo can have different results if the attributes
		//have different resolution.
		//e.g. current has microsecond resolution, but prev has millis.
		if (current.lastModifiedTime().toMillis() != prev.lastModifiedTime().toMillis()) {
			return true;
		}
		return false;
	}

	/**
	 * Gets the root file provider key.
	 * 
	 * @return The provider key.
	 */
	public RootFileProviderKey getProviderKey() {
		return providerKey;
	}

	/**
	 * Gets the file path.
	 * 
	 * @return The file path.
	 */
	public SakerPath getFilePath() {
		return filePath;
	}

	/**
	 * Gets the last modification time in milliseconds.
	 * 
	 * @return The last modification time.
	 */
	public long getLastModifiedMillis() {
		return lastModifiedMillis;
	}

	/**
	 * Gets the size of the file.
	 * 
	 * @return The size of the file.
	 */
	public long getSize() {
		return size;
	}

	@Override
	public boolean isChanged(ContentDescriptor o) {
		if (!(o instanceof FileAttributesContentDescriptor)) {
			return true;
		}
		FileAttributesContentDescriptor ocd = (FileAttributesContentDescriptor) o;
		return this.size != ocd.size || this.lastModifiedMillis != ocd.lastModifiedMillis
				|| !Objects.equals(this.providerKey, ocd.providerKey) || !Objects.equals(this.filePath, ocd.filePath);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(providerKey);
		out.writeObject(filePath);
		out.writeLong(lastModifiedMillis);
		out.writeLong(size);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		providerKey = (RootFileProviderKey) in.readObject();
		filePath = (SakerPath) in.readObject();
		lastModifiedMillis = in.readLong();
		size = in.readLong();
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(providerKey) * 31 + Objects.hashCode(filePath);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileAttributesContentDescriptor other = (FileAttributesContentDescriptor) obj;
		if (lastModifiedMillis != other.lastModifiedMillis)
			return false;
		if (size != other.size)
			return false;
		if (!Objects.equals(filePath, other.filePath))
			return false;
		if (!Objects.equals(providerKey, other.providerKey))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[size=" + size + ", lastModifiedMillis=" + lastModifiedMillis + ", "
				+ (filePath != null ? "filePath=" + filePath + ", " : "")
				+ (providerKey != null ? "providerKey=" + providerKey : "") + "]";
	}

}
