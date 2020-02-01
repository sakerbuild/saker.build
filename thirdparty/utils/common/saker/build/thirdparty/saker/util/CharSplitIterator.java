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
package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

class CharSplitIterator implements Iterator<CharSequence> {
	protected final CharSequence str;
	protected final char c;

	private int startIndex = 0;

	public CharSplitIterator(CharSequence str, char c) {
		this.str = str;
		this.c = c;
	}

	@Override
	public boolean hasNext() {
		return startIndex <= str.length();
	}

	@Override
	public CharSequence next() {
		if (startIndex > str.length()) {
			throw new NoSuchElementException("No next value.");
		}
		int idx = getNextSplitIndex(this.startIndex);
		CharSequence result = new SubCharSequence(str, startIndex, idx - startIndex);
		startIndex = idx + 1;
		return result;
	}

	protected int getNextSplitIndex(int startindex) {
		int idx = -1;
		int strlen = str.length();
		for (int i = startindex; i < strlen; i++) {
			if (str.charAt(i) == c) {
				idx = i;
				break;
			}
		}
		if (idx < 0) {
			idx = strlen;
		}
		return idx;
	}

	@Override
	public String toString() {
		return getRemaining().toString();
	}

	public CharSequence getRemaining() {
		return new SubCharSequence(str, startIndex, str.length() - startIndex);
	}

	public void reset() {
		startIndex = 0;
	}
}
