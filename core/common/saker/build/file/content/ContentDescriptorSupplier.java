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

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;

/**
 * Interface for customizing the algorithm of creating the default content descriptor for files on the file system.
 * <p>
 * Implementations of this interface is mainly used by {@link ContentDatabase} to specify how the differences in file
 * contents will be handled. The {@link CommonContentDescriptorSupplier common implementations} include logic for
 * creating content descriptors based on file attributes (size, modification time) or hashing the file contents (MD5).
 * <p>
 * Implementations should adhere to the {@link #equals(Object)} and {@link #hashCode()} contract.
 * 
 * @see DatabaseConfiguration
 * @see ContentDatabase
 */
public interface ContentDescriptorSupplier {
	/**
	 * Creates the content descriptor for the given location.
	 * 
	 * @param pathkey
	 *            The file location.
	 * @return The content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ContentDescriptor get(ProviderHolderPathKey pathkey) throws IOException;

	/**
	 * Creates the content descriptor for a file, optionally using the provided contents or attributes.
	 * 
	 * @param pathkey
	 *            The file location.
	 * @param bytes
	 *            The raw byte content of the file.
	 * @param attrs
	 *            The file attributes, or <code>null</code> if it is not available (<code>null</code> attributes doesn't
	 *            mean that the file doesn't exist).
	 * @return The content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default ContentDescriptor getUsingFileContent(ProviderHolderPathKey pathkey, ByteArrayRegion bytes,
			BasicFileAttributes attrs) throws IOException {
		if (attrs != null) {
			return getUsingFileAttributes(pathkey, attrs);
		}
		return get(pathkey);
	}

	/**
	 * Creates the content descriptor for a file, optionally using the provided attributes.
	 * 
	 * @param pathkey
	 *            The file location.
	 * @param attrs
	 *            The attributes of the file.
	 * @return The content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default ContentDescriptor getUsingFileAttributes(ProviderHolderPathKey pathkey, BasicFileAttributes attrs)
			throws IOException {
		return get(pathkey);
	}

	/**
	 * Creates a content descriptor calculating output.
	 * <p>
	 * Implementations can return a custom output stream which will receive the contents of the file. The
	 * {@link #getCalculatedOutput(ProviderHolderPathKey, ByteSink)} function will be called after the file writing is
	 * done, and the implementation can instantiate the content descriptor based on the calculated data.
	 * <p>
	 * This functionality is optional, not required to be supported by implementations.
	 * <p>
	 * Implementing this function for content hash based implementations can improve performance.
	 * 
	 * @return The output or <code>null</code> if this functionality is not supported.
	 */
	public default ByteSink getCalculatingOutput() {
		return null;
	}

	/**
	 * Computes the content descriptor based on the data calculated.
	 * 
	 * @param pathkey
	 *            The location of the file.
	 * @param calculatingoutput
	 *            The output which was returned from {@link #getCalculatingOutput()} and has the file contents written
	 *            to.
	 * @return The content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default ContentDescriptor getCalculatedOutput(ProviderHolderPathKey pathkey, ByteSink calculatingoutput)
			throws IOException {
		return get(pathkey);
	}

	@Override
	public int hashCode();

	/**
	 * Checks if this supplier will calculate the same content descriptors as the parameter given the same
	 * circumstances.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}
