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

class SynchronizedOutputStream extends OutputStream implements ByteSink {
	protected final OutputStream out;

	public SynchronizedOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public synchronized void write(int b) throws IOException {
		out.write(b);
	}

	@Override
	public synchronized void write(byte[] b) throws IOException {
		out.write(b);
	}

	@Override
	public synchronized void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}

	@Override
	public synchronized void flush() throws IOException {
		out.flush();
	}

	@Override
	public synchronized void close() throws IOException {
		out.close();
	}

	@Override
	public synchronized void write(ByteArrayRegion buf) throws IOException {
		buf.writeTo(out);
	}

	@Override
	public synchronized long readFrom(ByteSource in) throws IOException {
		return ByteSink.super.readFrom(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + out + "]";
	}
}
