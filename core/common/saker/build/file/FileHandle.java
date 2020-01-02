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

import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIInputStreamWrapper;

/**
 * Interface representing a reference to a file.
 * <p>
 * The interface specifies methods in addition to {@link StreamWritable}. Subclasses should override these to provide a
 * more efficient implementation.
 */
public interface FileHandle extends StreamWritable {
	/**
	 * Gets the name of the file.
	 * <p>
	 * The name of the file should be considered final and not change during the lifetime of the object.
	 * <p>
	 * The name may not be <code>null</code>, empty, <code>"."</code> or <code>".."</code>, must not contain slash
	 * characters (<code>'/'</code>, <code>'\\'</code>), and must not contain the colon character (<code>':'</code>).
	 * <p>
	 * Note that in some cases the above restriction may be violated, when root directories need to be represented. They
	 * will have a name according to the root path name they represent. I.e. They can be <code>"/"</code>, or drive
	 * names in <code>"drive:"</code> format. These root directories cannot be added to other directories.
	 * 
	 * @return The file name.
	 * @see SakerPathFiles#requireValidFileName(String)
	 */
	@RMICacheResult
	public String getName();

	/**
	 * Opens an {@link InputStream} to the contents of the file.
	 * 
	 * @return The opened input.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIWrap(RMIInputStreamWrapper.class)
	public default InputStream openInputStream() throws IOException {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			writeTo((OutputStream) baos);
			return new UnsyncByteArrayInputStream(baos.getBuffer(), 0, baos.size());
		}
	}

	/**
	 * Opens a {@link ByteSource} to the contents of the file.
	 * 
	 * @return The opened input.
	 * @throws IOException
	 *             In caes of I/O error.
	 */
	public default ByteSource openByteSource() throws IOException {
		return ByteSource.valueOf(openInputStream());
	}

	/**
	 * Gets the raw contents of the file as a byte array.
	 * 
	 * @return The raw contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default ByteArrayRegion getBytes() throws IOException {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			writeTo((OutputStream) baos);
			return baos.toByteArrayRegion();
		}
	}

	/**
	 * Gets the contents of the file as a {@link String}.
	 * <p>
	 * The default implementation converts the raw byte contents of the file is to string by decoding it as UTF-8
	 * encoded data.
	 * 
	 * @return The file contents as string.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default String getContent() throws IOException {
		return getBytes().toString();
	}

}
