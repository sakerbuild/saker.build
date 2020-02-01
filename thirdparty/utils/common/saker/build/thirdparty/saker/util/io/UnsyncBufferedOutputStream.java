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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Output stream class similar to {@link BufferedOutputStream}, but it does not synchronize its methods and therefore is
 * not thread safe.
 * <p>
 * Closing this output stream will close its subject too.
 * <p>
 * This stream is not thread safe.
 */
public class UnsyncBufferedOutputStream extends OutputStream implements ByteSink {
	/**
	 * The default buffer size.
	 */
	private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

	/**
	 * The subject output stream.
	 */
	protected final OutputStream out;

	/**
	 * The array holding the buffered data.
	 */
	protected final byte[] buffer;
	/**
	 * The number of bytes buffered in the {@link #buffer}.
	 */
	protected int count;

	/**
	 * Creates a new buffered output stream for the given subject input stream.
	 * <p>
	 * The default buffer size is used: {@value #DEFAULT_BUFFER_SIZE}.
	 * 
	 * @param out
	 *            The subject output stream.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public UnsyncBufferedOutputStream(OutputStream out) throws NullPointerException {
		this(out, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * Creates a new instance with the given buffer size.
	 * <p>
	 * The buffer size should be chosen to be reasonable. Choosing very small numbers can degrade the performance.
	 * 
	 * @param out
	 *            The subject output stream.
	 * @param buffersize
	 *            The buffer size.
	 * @throws NullPointerException
	 *             If the output stream is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the buffer size is less than 1.
	 */
	public UnsyncBufferedOutputStream(OutputStream out, int buffersize)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(out, "output stream");
		if (buffersize < 1) {
			throw new IllegalArgumentException("buffer size < 1: " + buffersize);
		}
		this.out = out;
		this.buffer = new byte[buffersize];
	}

	/**
	 * Flushes any buffered data in this output stream.
	 * <p>
	 * Only the buffered data is flushed, {@link OutputStream#flush()} is not called on the subject stream.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void flushBuffer() throws IOException {
		if (count > 0) {
			out.write(buffer, 0, count);
			count = 0;
		}
	}

	@Override
	public void write(int b) throws IOException {
		if (count >= buffer.length) {
			flushBuffer();
		}
		buffer[count++] = (byte) b;
	}

	@Override
	public void write(byte[] b) throws IOException {
		this.write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int remcount = buffer.length - count;
		if (len <= remcount) {
			System.arraycopy(b, off, buffer, count, len);
			count += len;
			return;
		}
		// the request length exceeds the available space
		// flush the buffer and write the bytes without buffering
		flushBuffer();
		if (len >= buffer.length) {
			out.write(b, off, len);
		} else {
			//the bytes fit in the emptied buffer
			System.arraycopy(b, off, buffer, 0, len);
			count += len;
		}
	}

	@Override
	public void flush() throws IOException {
		flushBuffer();
		out.flush();
	}

	@Override
	public void close() throws IOException {
		try {
			flushBuffer();
		} finally {
			out.close();
		}
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		buf.writeTo((OutputStream) this);
	}

}
