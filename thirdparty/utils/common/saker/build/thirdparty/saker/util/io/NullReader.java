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

import java.io.Reader;
import java.nio.CharBuffer;

class NullReader extends Reader {
	public static final NullReader INSTANCE = new NullReader();

	private NullReader() {
	}

	@Override
	public int read(char[] cbuf, int off, int len) {
		return -1;
	}

	@Override
	public int read(CharBuffer target) {
		return -1;
	}

	@Override
	public int read() {
		return -1;
	}

	@Override
	public int read(char[] cbuf) {
		return -1;
	}

	@Override
	public long skip(long n) {
		return 0;
	}

	@Override
	public boolean ready() {
		return true;
	}

	@Override
	public void close() {
	}

}
