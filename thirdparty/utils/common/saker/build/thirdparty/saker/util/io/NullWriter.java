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

import java.io.Writer;

class NullWriter extends Writer {
	public static final NullWriter INSTANCE = new NullWriter();

	private NullWriter() {
	}

	@Override
	public void write(int c) {
	}

	@Override
	public void write(char[] cbuf) {
	}

	@Override
	public void write(String str) {
	}

	@Override
	public void write(String str, int off, int len) {
	}

	@Override
	public Writer append(CharSequence csq) {
		return this;
	}

	@Override
	public Writer append(CharSequence csq, int start, int end) {
		return this;
	}

	@Override
	public Writer append(char c) {
		return this;
	}

	@Override
	public void write(final char[] cbuf, final int off, final int len) {
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

}
