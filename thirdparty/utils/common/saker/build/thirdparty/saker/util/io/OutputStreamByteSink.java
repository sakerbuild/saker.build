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
import java.io.OutputStream;
import java.util.Objects;

/**
 * {@link OutputStream} and {@link ByteSink} implementation that forwards its calls to an underlying
 * {@link OutputStream}.
 */
public class OutputStreamByteSink extends OutputStream implements ByteSink {
	static final int MAX_WRITE_COPY_SIZE = 4 * 1024 * 1024;

	/**
	 * The underlying {@link OutputStream}.
	 */
	protected final OutputStream os;

	/**
	 * Creates a new instance with the given underlying output stream.
	 * 
	 * @param os
	 *            The output stream.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public OutputStreamByteSink(OutputStream os) throws NullPointerException {
		Objects.requireNonNull(os, "output stream");
		this.os = os;
	}

	@Override
	public void write(int b) throws IOException {
		os.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		os.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		os.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		os.flush();
	}

	@Override
	public void close() throws IOException {
		os.close();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + os + "]";
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		buf.writeTo(os);
	}
}
