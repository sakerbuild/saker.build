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
 * {@link OutputStream} and {@link ByteSink} implementation that forwards its calls to an underlying {@link ByteSink}.
 */
public class ByteSinkOutputStream extends OutputStream implements ByteSink {
	/**
	 * The underlying {@link ByteSink}.
	 */
	protected final ByteSink sink;

	/**
	 * Creates a new instance with the given underlying byte sink.
	 * 
	 * @param sink
	 *            The byte sink.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ByteSinkOutputStream(ByteSink sink) throws NullPointerException {
		Objects.requireNonNull(sink, "byte sink");
		this.sink = sink;
	}

	@Override
	public void write(int b) throws IOException {
		sink.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		sink.write(ByteArrayRegion.wrap(b));
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		sink.write(ByteArrayRegion.wrap(b, off, len));
	}

	@Override
	public void flush() throws IOException {
		sink.flush();
	}

	@Override
	public void close() throws IOException {
		sink.close();
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		sink.write(buf);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + sink + "]";
	}
}
