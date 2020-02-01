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

final class NullOutputStream extends OutputStream implements ObjectOutputByteSink, Appendable {
	public static final NullOutputStream INSTANCE = new NullOutputStream();

	private NullOutputStream() {
	}

	@Override
	public void write(int b) {
	}

	@Override
	public void write(byte[] b) {
	}

	@Override
	public void write(byte[] b, int off, int len) {
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
	}

	@Override
	public void writeByte(int v) throws IOException {
	}

	@Override
	public void writeShort(int v) throws IOException {
	}

	@Override
	public void writeChar(int v) throws IOException {
	}

	@Override
	public void writeInt(int v) throws IOException {
	}

	@Override
	public void writeLong(long v) throws IOException {
	}

	@Override
	public void writeFloat(float v) throws IOException {
	}

	@Override
	public void writeDouble(double v) throws IOException {
	}

	@Override
	public void writeBytes(String s) throws IOException {
	}

	@Override
	public void writeChars(String s) throws IOException {
	}

	@Override
	public void writeUTF(String s) throws IOException {
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
	}

	@Override
	public Appendable append(CharSequence csq) throws IOException {
		return this;
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end) throws IOException {
		return this;
	}

	@Override
	public Appendable append(char c) throws IOException {
		return this;
	}

	@Override
	public void writeObject(Object obj) throws IOException {
	}
}