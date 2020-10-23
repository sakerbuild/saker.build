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

class SynchronizedByteSource extends InputStream implements ByteSource {
	protected final ByteSource in;

	public SynchronizedByteSource(ByteSource in) {
		this.in = in;
	}

	@Override
	public synchronized int read(ByteRegion buffer) throws IOException {
		return in.read(buffer);
	}

	@Override
	public synchronized int read() throws IOException {
		return in.read();
	}

	@Override
	public synchronized ByteArrayRegion read(int count) throws IOException {
		return in.read(count);
	}

	@Override
	public synchronized long writeTo(ByteSink out) throws IOException {
		return in.writeTo(out);
	}

	@Override
	public synchronized int read(byte[] b) throws IOException {
		return in.read(ByteArrayRegion.wrap(b));
	}

	@Override
	public synchronized int read(byte[] b, int off, int len) throws IOException {
		return in.read(ByteArrayRegion.wrap(b, off, len));
	}

	@Override
	public synchronized long skip(long n) throws IOException {
		return in.skip(n);
	}

	@Override
	public synchronized int available() throws IOException {
		return super.available();
	}

	@Override
	public synchronized void close() throws IOException {
		in.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		super.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		super.reset();
	}

	@Override
	public synchronized boolean markSupported() {
		return super.markSupported();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + in + "]";
	}
}
