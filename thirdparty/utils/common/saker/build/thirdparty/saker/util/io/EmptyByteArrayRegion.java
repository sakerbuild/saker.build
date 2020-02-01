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

import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.charset.Charset;

import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

final class EmptyByteArrayRegion extends ByteArrayRegion {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public EmptyByteArrayRegion() {
	}

	@Override
	public byte[] copy() {
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public void put(int index, byte b) throws IndexOutOfBoundsException {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public byte[] copyArrayRegion(int offset, int length) throws IndexOutOfBoundsException, IllegalArgumentException {
		ArrayUtils.requireArrayRangeLength(0, offset, length);
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public byte get(int index) throws IndexOutOfBoundsException {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public int getLength() {
		return 0;
	}

	@Override
	public void put(int index, ByteArrayRegion bytes) throws IndexOutOfBoundsException, NullPointerException {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public byte[] getArray() {
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@SuppressWarnings("static-method")
	private Object readResolve() {
		return ByteArrayRegion.EMPTY;
	}

	@Override
	public void put(int index, byte[] bytes, int offset, int len) {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public void put(int index, byte[] bytes) {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public byte[] copyOptionally() {
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public long writeTo(OutputStream os) throws IOException {
		return 0;
	}

	@Override
	public long writeTo(DataOutput out) throws IOException {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public boolean regionEquals(ByteArrayRegion region) {
		return region.isEmpty();
	}

	@Override
	public String toString() {
		return "";
	}

	@Override
	public String toString(Charset charset) {
		return "";
	}
}
