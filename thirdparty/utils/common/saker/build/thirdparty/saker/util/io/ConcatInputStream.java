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
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayIterator;

/**
 * Input stream class for concatenating the data of multiple input streams into one stream.
 * <p>
 * <b>Important:</b> This class doesn't close the streams that were passed to it. Each stream should be closed by the
 * caller after the {@link ConcatInputStream} instance is closed.
 * <p>
 * This stream takes multiple {@link InputStream InputStreams} during its construction, and it will iterate over them
 * when read operations are performed.
 * <p>
 * When a reading operation is performed, the class will try to read from the first input stream. When a stream returns
 * zero bytes from a read operation, the class will move to the next stream and try reading from that. This repeats
 * until no more streams are available, in which case the class will return end of stream for the reading operations.
 * <p>
 * This input stream class is not thread safe.
 */
public class ConcatInputStream extends InputStream {
	/*
	 * The implementation only attempts to forward the call to a single input stream
	 * in a single call to this input stream, so the possible exceptions won't be 
	 * swallowed by the ConcatInputStream.
	 * Otherwise in case of read(byte[], int, int), if we read from multiple streams
	 * until the buffer is filled, then if the read from as second stream fails with an exception
	 * but we already successfully read from a previous stream, then we need to return the bytes 
	 * of the first read, yet we shouldn't just swallow the exception.
	 */

	private InputStream currentInputStream;
	private Iterator<? extends InputStream> iterator;

	/**
	 * Creates a new instance for the given input streams.
	 * <p>
	 * Modifying the argument array externally may result in incorrect operation.
	 * <p>
	 * <code>null</code> {@link InputStream InputStreams} in the argument array will be skipped.
	 * 
	 * @param inputstreams
	 *            The input streams.
	 * @throws NullPointerException
	 *             If the argument array is <code>null</code>.
	 */
	public ConcatInputStream(InputStream... inputstreams) throws NullPointerException {
		this(new ArrayIterator<>(inputstreams));
	}

	/**
	 * Creates a new instance for the given input streams.
	 * <p>
	 * <code>null</code> {@link InputStream InputStreams} in the argument iterable will be skipped.
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
	 * <code>null</code> {@link InputStream InputStreams} in the argument iterator will be skipped.
	 * 
	 * @param streamiterator
	 *            An iterator for input streams.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public ConcatInputStream(Iterator<? extends InputStream> streamiterator) throws NullPointerException {
		Objects.requireNonNull(streamiterator, "stream iterator");
		this.iterator = streamiterator;
		this.currentInputStream = nextStream(streamiterator);
	}

	@Override
	public int read() throws IOException {
		Iterator<? extends InputStream> it = iterator;
		if (it == null) {
			throw new IOException("closed.");
		}
		for (InputStream is = currentInputStream; is != null; currentInputStream = (is = nextStream(it))) {
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
		Iterator<? extends InputStream> it = iterator;
		if (it == null) {
			throw new IOException("closed.");
		}
		for (InputStream is = currentInputStream; is != null; currentInputStream = (is = nextStream(it))) {
			int read = is.read(b, off, len);
			if (read > 0) {
				return read;
			}
		}
		return -1;
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
		for (InputStream is = currentInputStream; is != null; currentInputStream = (is = nextStream(it))) {
			long isskip = is.skip(n);
			if (isskip > 0) {
				return isskip;
			}
		}
		return 0;
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

	private static InputStream nextStream(Iterator<? extends InputStream> it) {
		while (it.hasNext()) {
			InputStream is = it.next();
			if (is == null) {
				//skip nulls (this is likely an invalid input for the ConcatInputStream, but we're better be error tolerant)
				continue;
			}
			return is;
		}
		return null;
	}
}
