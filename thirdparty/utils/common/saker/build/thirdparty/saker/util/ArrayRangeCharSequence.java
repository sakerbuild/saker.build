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

class ArrayRangeCharSequence implements CharSequence {
	protected char[] array;
	protected int offset;
	protected int length;

	public ArrayRangeCharSequence(char[] array, int offset, int length) {
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public char charAt(int index) {
		if (index < 0 || index >= length) {
			throw new IndexOutOfBoundsException(index + " for length: " + length);
		}
		return array[offset + index];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		ArrayUtils.requireArrayStartEndRangeLength(this.length, start, end);
		int nlen = end - start;
		if (nlen == 0) {
			return "";
		}
		return new ArrayRangeCharSequence(array, offset + start, nlen);
	}

	@Override
	public String toString() {
		return new String(array, offset, length);
	}

}
