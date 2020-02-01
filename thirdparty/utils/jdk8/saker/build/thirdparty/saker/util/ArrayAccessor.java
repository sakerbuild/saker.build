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

class ArrayAccessor {
	private ArrayAccessor() {
		throw new UnsupportedOperationException();
	}

	// range checks are done by the caller
	public static boolean equals(byte[] a, int aFromIndex, byte[] b, int bFromIndex, int numberOfBytes) {
		for (int i = 0; i < numberOfBytes; i++) {
			if (a[aFromIndex + i] != b[bFromIndex + i]) {
				return false;
			}
		}
		return true;
	}

	public static int mismatch(byte[] a, int aFromIndex, byte[] b, int bFromIndex, int numberOfBytes) {
		for (int i = 0; i < numberOfBytes; i++) {
			if (a[aFromIndex + i] != b[bFromIndex + i]) {
				return i;
			}
		}
		return -1;
	}
}
