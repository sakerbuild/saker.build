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
import java.io.OutputStream;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;

/**
 * {@link StreamWritable} implementation that is backed by a byte array.
 */
@PublicApi
public class ByteArrayStreamWritable implements StreamWritable {
	private ByteArrayRegion data;

	/**
	 * Creates a new instance for the given array.
	 * 
	 * @param data
	 *            The byte array.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ByteArrayStreamWritable(byte[] data) throws NullPointerException {
		this.data = ByteArrayRegion.wrap(data);
	}

	/**
	 * Creates a new instance for the given array region.
	 * 
	 * @param data
	 *            The byte array region.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ByteArrayStreamWritable(ByteArrayRegion data) throws NullPointerException {
		Objects.requireNonNull(data, "data");
		this.data = data;
	}

	/**
	 * Gets the byte array region that this class holds.
	 * 
	 * @return The byte array region.
	 */
	public ByteArrayRegion getData() {
		return data;
	}

	@Override
	public void writeTo(OutputStream os) throws IOException {
		data.writeTo(os);
	}

	@Override
	public void writeTo(ByteSink sink) throws IOException {
		sink.write(data);
	}

}
