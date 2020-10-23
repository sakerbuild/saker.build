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
import java.util.concurrent.locks.ReentrantLock;

class LockedOutputStream extends OutputStream implements ByteSink {
	protected final OutputStream out;
	protected final ReentrantLock lock = new ReentrantLock();

	public LockedOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(int b) throws IOException {
		lock.lock();
		try {
			out.write(b);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		lock.lock();
		try {
			out.write(b);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		lock.lock();
		try {
			out.write(b, off, len);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void flush() throws IOException {
		lock.lock();
		try {
			out.flush();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close() throws IOException {
		lock.lock();
		try {
			out.close();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		lock.lock();
		try {
			buf.writeTo(out);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public long readFrom(ByteSource in) throws IOException {
		lock.lock();
		try {
			return ByteSink.super.readFrom(in);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + out + "]";
	}
}
