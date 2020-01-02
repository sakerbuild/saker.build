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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Objects;

/**
 * Externalizable {@link BasicFileAttributes} implementation for {@link SakerFileProvider} usage.
 */
public final class FileEntry implements BasicFileAttributes, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * File type representing an unknown/non-existing type.
	 */
	public static final int TYPE_NULL = 0;
	/**
	 * File type representing a simple file.
	 * 
	 * @see BasicFileAttributes#isRegularFile()
	 */
	public static final int TYPE_FILE = 1;
	/**
	 * File type representing a directory.
	 * 
	 * @see BasicFileAttributes#isDirectory()
	 */
	public static final int TYPE_DIRECTORY = 2;
	/**
	 * File type representing a link.
	 * 
	 * @see BasicFileAttributes#isSymbolicLink()
	 */
	public static final int TYPE_LINK = 3;
	/**
	 * File type representing some unknown other type.
	 * 
	 * @see BasicFileAttributes#isOther()
	 */
	public static final int TYPE_OTHER = 4;

	private int type = TYPE_NULL;
	private long size;
	private long lastModifiedMillis;

	/**
	 * For {@link Externalizable}.
	 */
	public FileEntry() {
	}

	/**
	 * Creates a new instance based on the argument attributes.
	 * 
	 * @param attributes
	 *            The attributes to base this instance on.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public FileEntry(BasicFileAttributes attributes) throws NullPointerException {
		Objects.requireNonNull(attributes, "attributes");
		this.type = attributesToFileType(attributes);
		this.lastModifiedMillis = attributes.lastModifiedTime().toMillis();
		this.size = attributes.size();
	}

	/**
	 * Creates a new instance with the specified arguments.
	 * 
	 * @param type
	 *            The type of the file.
	 * @param size
	 *            The size of the file in bytes.
	 * @param lastModified
	 *            The last modified time.
	 * @throws NullPointerException
	 *             If last modified time is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the type is not a valid value declared by this class.
	 */
	public FileEntry(int type, long size, FileTime lastModified) throws NullPointerException, IllegalArgumentException {
		if (type < TYPE_NULL || type > TYPE_OTHER) {
			throw new IllegalArgumentException("Invalid type: " + type);
		}
		this.type = type;
		this.size = size;
		this.lastModifiedMillis = lastModified.toMillis();
	}

	/**
	 * Gets the type of the file.
	 * <p>
	 * The file type is one of the constants declared in this class starting with <code>TYPE_</code>.
	 * 
	 * @return The type of the file.
	 */
	public int getType() {
		return type;
	}

	/**
	 * Gets the size of the file in bytes.
	 * 
	 * @return The size of the file.
	 */
	public long getSize() {
		return size;
	}

	/**
	 * Gets the last modified time of the file.
	 * <p>
	 * The time is the elapsed milliseconds since the epoch defined by {@link FileTime#toMillis()}.
	 * 
	 * @return The last modified time in milliseconds.
	 */
	public long getLastModifiedMillis() {
		return lastModifiedMillis;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		//other fields intentionally left out
		result = prime * result + type;
		result = prime * result + Long.hashCode(size);
		result = prime * result + Long.hashCode(lastModifiedMillis);
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
		FileEntry other = (FileEntry) obj;
		if (lastModifiedMillis != other.lastModifiedMillis)
			return false;
		if (size != other.size)
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public FileTime lastModifiedTime() {
		return FileTime.fromMillis(lastModifiedMillis);
	}

	@Override
	public FileTime lastAccessTime() {
		return lastModifiedTime();
	}

	@Override
	public FileTime creationTime() {
		return lastModifiedTime();
	}

	@Override
	public boolean isRegularFile() {
		return type == TYPE_FILE;
	}

	@Override
	public boolean isDirectory() {
		return type == TYPE_DIRECTORY;
	}

	@Override
	public boolean isSymbolicLink() {
		return type == TYPE_LINK;
	}

	@Override
	public boolean isOther() {
		return type == TYPE_OTHER;
	}

	@Override
	public long size() {
		return size;
	}

	@Override
	public Object fileKey() {
		return null;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + size + " bytes, " + new Date(lastModifiedMillis) + " - "
				+ lastModifiedMillis + ", type=" + type + "]";
	}

	/**
	 * Gets the file type defined by this class from the specified attributes.
	 * 
	 * @param attributes
	 *            The attributes.
	 * @return One of the file type constants declared by this class.
	 */
	public static int attributesToFileType(BasicFileAttributes attributes) {
		if (attributes.isRegularFile()) {
			return FileEntry.TYPE_FILE;
		}
		if (attributes.isDirectory()) {
			return FileEntry.TYPE_DIRECTORY;
		}
		if (attributes.isSymbolicLink()) {
			return FileEntry.TYPE_LINK;
		}
		return FileEntry.TYPE_OTHER;
	}

	/**
	 * Externalizes this object to the specified data output stream.
	 * 
	 * @param out
	 *            The data output.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(type);
		out.writeLong(size);
		out.writeLong(lastModifiedMillis);
	}

	/**
	 * Reads the externalized data from the specified data input stream.
	 * 
	 * @param in
	 *            The data input.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void readExternal(DataInput in) throws IOException {
		type = in.readInt();
		size = in.readLong();
		lastModifiedMillis = in.readLong();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		writeExternal((DataOutput) out);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		readExternal((DataInput) in);
	}
}