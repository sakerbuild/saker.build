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
package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * {@link InputStream} and {@link ByteSource} implementation that forwards its calls to an underlying
 * {@link ByteSource}.
 */
public class ByteSourceInputStream extends InputStream implements ByteSource {
	/**
	 * The underlying {@link ByteSource}.
	 */
	protected final ByteSource source;

	/**
	 * Creates a new instance with the given underlying byte source.
	 * 
	 * @param source
	 *            The byte source.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ByteSourceInputStream(ByteSource source) throws NullPointerException {
		Objects.requireNonNull(source, "byte source");
		this.source = source;
	}

	@Override
	public int read() throws IOException {
		return source.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return source.read(ByteArrayRegion.wrap(b, 0, b.length));
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return source.read(ByteArrayRegion.wrap(b, off, len));
	}

	@Override
	public ByteArrayRegion read(int count) throws IOException {
		return source.read(count);
	}

	@Override
	public long writeTo(ByteSink out) throws IOException {
		return source.writeTo(out);
	}

	@Override
	public long skip(long n) throws IOException {
		return ByteSource.super.skip(n);
	}

	@Override
	public void close() throws IOException {
		source.close();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + source + "]";
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		return source.read(buffer);
	}

}
