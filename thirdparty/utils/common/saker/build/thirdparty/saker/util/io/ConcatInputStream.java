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
import java.util.Iterator;

import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * Input stream class for concatenating the data of multiple input streams into one stream.
 * <p>
 * <b>Important: </b>This class doesn't close the streams that were passed to it.
 * <p>
 * This stream takes multiple {@link InputStream InputStreams} during its construction, and it will iterate over them
 * when read operations are performed.
 * <p>
 * When a reding operation is performed, the class will try to read from the first input stream. When a stream returns
 * zero bytes from a read operation, the class will move to the next stream and try reading from that. This repeats
 * until no more streams are available, in which case the class will return end of stream for the reading operations.
 * <p>
 * This input stream class is not thread safe.
 */
public class ConcatInputStream extends InputStream {

	private InputStream currentInputStream;
	private Iterator<? extends InputStream> iterator;

	/**
	 * Creates a new instance for the given input streams.
	 * <p>
	 * Modifying the argument array externally may result in incorrect operation.
	 * <p>
	 * If any input stream in the argument is <code>null</code>, the streams after that will not be used.
	 * 
	 * @param inputstreams
	 *            The input streams.
	 */
	public ConcatInputStream(InputStream... inputstreams) {
		this(ImmutableUtils.asUnmodifiableArrayList(inputstreams).iterator());
	}

	/**
	 * Creates a new instance for the given input streams.
	 * <p>
	 * If any input stream in the argument is <code>null</code>, the streams after that will not be used.
	 * 
	 * @param inputstreams
	 *            An iterable of input streams.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcatInputStream(Iterable<? extends InputStream> inputstreams) throws NullPointerException {
		this(inputstreams.iterator());
	}

	/**
	 * Creates a new instance for the given input streams specified by an iterator.
	 * <p>
	 * If any input stream in the argument is <code>null</code>, the streams after that will not be used.
	 * 
	 * @param isiterator
	 *            An iterator for input streams.
	 */
	public ConcatInputStream(Iterator<? extends InputStream> isiterator) {
		this.iterator = isiterator;
		this.currentInputStream = iterator.hasNext() ? iterator.next() : null;
	}

	@Override
	public int read() throws IOException {
		Iterator<? extends InputStream> it = iterator;
		if (it == null) {
			throw new IOException("closed.");
		}
		for (InputStream is = currentInputStream; is != null; is = iterator.hasNext() ? it.next() : null) {
			int res = is.read();
			if (res >= 0) {
				return res;
			}
		}
		return -1;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0) {
			return 0;
		}
		if (currentInputStream == null) {
			return -1;
		}
		Iterator<? extends InputStream> it = iterator;
		if (it == null) {
			throw new IOException("closed.");
		}
		int read = 0;
		for (; read < len && currentInputStream != null; currentInputStream = it.hasNext() ? it.next() : null) {
			int isread = currentInputStream.read(b, off + read, len - read);
			if (isread > 0) {
				read += isread;
			}
			if (read == len) {
				return read;
			}
		}
		return read == 0 ? -1 : read;
	}

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		Iterator<? extends InputStream> it = iterator;
		if (it == null) {
			throw new IOException("closed.");
		}
		final long originaln = n;
		for (; n > 0 && currentInputStream != null; currentInputStream = it.hasNext() ? it.next() : null) {
			long isskip = currentInputStream.skip(n);
			if (isskip > 0) {
				n -= isskip;
			}
			if (n == 0) {
				break;
			}
		}
		return originaln - n;
	}

	@Override
	public int available() throws IOException {
		InputStream is = currentInputStream;
		if (is != null) {
			return Math.max(0, is.available());
		}
		return 0;
	}

	@Override
	public void close() throws IOException {
		currentInputStream = null;
		iterator = null;
	}
}
