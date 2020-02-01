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

class SubCharSequence implements CharSequence {
	protected CharSequence subject;
	protected int offset;
	protected int length;

	public SubCharSequence(CharSequence subject, int offset, int length) {
		this.subject = subject;
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
		return subject.charAt(this.offset + index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		ArrayUtils.requireArrayStartEndRangeLength(this.length, start, end);
		int nlen = end - start;
		if (nlen == 0) {
			return "";
		}
		return new SubCharSequence(subject, this.offset + start, nlen);
	}

	public CharSequence subOffsetLengthSequence(int offset, int length) {
		ArrayUtils.requireArrayRangeLength(this.length, offset, length);
		return new SubCharSequence(subject, this.offset + offset, length);
	}

	@Override
	public String toString() {
		return subject.subSequence(offset, offset + length).toString();
	}
}