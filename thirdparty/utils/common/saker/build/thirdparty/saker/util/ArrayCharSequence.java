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

class ArrayCharSequence implements CharSequence {
	protected char[] array;

	public ArrayCharSequence(char[] array) {
		this.array = array;
	}

	@Override
	public int length() {
		return array.length;
	}

	@Override
	public char charAt(int index) {
		return array[index];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return new SubCharSequence(this, start, end - start);
	}

	@Override
	public String toString() {
		return new String(array);
	}

}
