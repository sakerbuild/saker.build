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
import java.util.concurrent.locks.ReentrantLock;

class LockedInputStream extends InputStream implements ByteSource {
	protected final InputStream in;
	protected final ReentrantLock lock = new ReentrantLock();

	public LockedInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		lock.lock();
		try {
			return StreamUtils.readFromStream(in, buffer);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int read() throws IOException {
		lock.lock();
		try {
			return in.read();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public ByteArrayRegion read(int count) throws IOException {
		lock.lock();
		try {
			return ByteSource.super.read(count);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public long writeTo(ByteSink out) throws IOException {
		lock.lock();
		try {
			return ByteSource.super.writeTo(out);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		lock.lock();
		try {
			return in.read(b);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		lock.lock();
		try {
			return in.read(b, off, len);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public long skip(long n) throws IOException {
		lock.lock();
		try {
			return in.skip(n);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int available() throws IOException {
		lock.lock();
		try {
			return in.available();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close() throws IOException {
		lock.lock();
		try {
			in.close();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void mark(int readlimit) {
		lock.lock();
		try {
			in.mark(readlimit);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void reset() throws IOException {
		lock.lock();
		try {
			in.reset();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean markSupported() {
		lock.lock();
		try {
			return in.markSupported();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + in + "]";
	}
}
