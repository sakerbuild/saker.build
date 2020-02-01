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

/**
 * Filter input stream that only allows a fixed amount of bytes to be read from its subject.
 * <p>
 * This input stream will report the input to as end of stream when the predetermined amount of bytes have been read.
 * Skipping counts towards reading.
 * <p>
 * Closing this stream will close its subject, and will not allow more bytes to be read from it.
 * <p>
 * This input stream class is not thread safe.
 */
public class LimitInputStream extends InputStream implements ByteSource {
	/**
	 * The limited input stream.
	 */
	protected InputStream in;
	/**
	 * The remaining number of bytes that can be read from the {@linkplain #in stream}.
	 */
	protected int remaining;

	/**
	 * Creates a new instance for the given input stream and with the specified byte count limit.
	 * 
	 * @param in
	 *            The input stream.
	 * @param limit
	 *            The number of bytes that can be maximally read from the stream.
	 */
	public LimitInputStream(InputStream in, int limit) {
		this.in = in;
		this.remaining = limit;
	}

	/**
	 * Gets the remaining number of bytes that is allowed to be read from the stream.
	 * <p>
	 * If this method returns a positive integer, that does not mean that this stream actually has that number of bytes
	 * available, but only that it is allowed to be read.
	 * <p>
	 * The return value may be negative, in which case it means 0.
	 * 
	 * @return The number of bytes that can be read.
	 */
	public int getRemainingLimit() {
		return remaining;
	}

	@Override
	public int read() throws IOException {
		if (remaining <= 0) {
			return -1;
		}
		int r = in.read();
		if (r >= 0) {
			--remaining;
		}
		return r;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return this.read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) {
			return 0;
		}
		if (remaining <= 0) {
			return -1;
		}
		len = Math.min(len, remaining);
		int result = in.read(b, off, len);
		if (result > 0) {
			remaining -= result;
		}
		return result;
	}

	@Override
	public int read(ByteRegion buffer) throws IOException, NullPointerException {
		return StreamUtils.readFromStream(in, buffer);
	}

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		if (remaining <= 0) {
			return 0;
		}
		long result = in.skip(Math.min(n, remaining));
		if (result > 0) {
			if (result >= remaining) {
				remaining = 0;
			} else {
				remaining -= result;
			}
		}
		return result;
	}

	@Override
	public int available() throws IOException {
		return Math.min(in.available(), remaining);
	}

	@Override
	public void close() throws IOException {
		remaining = 0;
		in.close();
	}

	@Override
	public void mark(int readlimit) {
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	@Override
	public boolean markSupported() {
		return false;
	}

}
