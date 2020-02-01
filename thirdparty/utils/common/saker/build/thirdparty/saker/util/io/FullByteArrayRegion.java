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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class FullByteArrayRegion extends ByteArrayRegion {
	private static final long serialVersionUID = 1L;

	private byte[] array;

	/**
	 * For {@link Externalizable}.
	 */
	public FullByteArrayRegion() {
	}

	/*default*/ FullByteArrayRegion(byte[] array) {
		this.array = array;
	}

	@Override
	public byte[] getArray() {
		return array;
	}

	@Override
	public int getLength() {
		return array.length;
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public byte[] copyOptionally() {
		return array;
	}

	@Override
	public long writeTo(OutputStream os) throws IOException {
		os.write(array, 0, array.length);
		return array.length;
	}

	@Override
	public long writeTo(DataOutput out) throws IOException {
		out.write(array, 0, array.length);
		return array.length;
	}

	@Override
	public boolean regionEquals(ByteArrayRegion region) {
		int tlen = array.length;
		int rlen = region.getLength();
		if (tlen != rlen) {
			return false;
		}
		byte[] tarray = array;
		byte[] rarray = region.getArray();
		int roffset = region.getOffset();
		for (int i = 0; i < tlen; i++) {
			if (tarray[i] != rarray[roffset + i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public byte[] copyArrayRegion(int offset, int length) throws IndexOutOfBoundsException, IllegalArgumentException {
		return Arrays.copyOfRange(array, offset, offset + length);
	}

	@Override
	public byte get(int index) throws IndexOutOfBoundsException {
		return array[index];
	}

	@Override
	public void put(int index, byte[] bytes, int offset, int len) {
		System.arraycopy(bytes, offset, array, index, len);
	}

	@Override
	public void put(int index, byte[] bytes) {
		System.arraycopy(bytes, 0, array, index, bytes.length);
	}

	@Override
	public boolean isEmpty() {
		//this class is never constructed for empty arrays
		return false;
	}

	@Override
	public void put(int index, byte b) {
		array[index] = b;
	}

	@Override
	public void put(int index, ByteArrayRegion bytes) {
		put(index, bytes.getArray(), bytes.getOffset(), bytes.getLength());
	}

	@Override
	public String toString() {
		return toString(StandardCharsets.UTF_8);
	}

	@Override
	public String toString(Charset charset) {
		return new String(array, charset);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(array.length);
		out.write(array, 0, array.length);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int length = in.readInt();
		array = new byte[length];
		in.readFully(array);
	}

}
