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

final class OffsetLengthByteArrayRegion extends ByteArrayRegion {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public OffsetLengthByteArrayRegion() {
	}

	private byte[] array;
	private int offset;
	private int length;

	/*default*/ OffsetLengthByteArrayRegion(byte[] array, int offset, int len) {
		this.array = array;
		this.offset = offset;
		this.length = len;
	}

	@Override
	public byte[] getArray() {
		return array;
	}

	@Override
	public boolean isEmpty() {
		//this class is never constructed for empty arrays
		return false;
	}

	@Override
	public int getLength() {
		return length;
	}

	@Override
	public int getOffset() {
		return offset;
	}

	@Override
	public void put(int index, byte b) {
		index += this.offset;
		ByteRegion.checkRange(index, 1, this.offset, this.length);
		array[index] = b;
	}

	@Override
	public void put(int index, byte[] bytes, int offset, int len) {
		index += this.offset;
		ByteRegion.checkRange(index, len, this.offset, this.length);
		System.arraycopy(bytes, offset, array, index, len);
	}

	@Override
	public void put(int index, ByteArrayRegion bytes) {
		put(index, bytes.getArray(), bytes.getOffset(), bytes.getLength());
	}

	@Override
	public byte[] copyOptionally() {
		if (offset != 0 || length != array.length) {
			return Arrays.copyOfRange(array, offset, offset + length);
		}
		return array;
	}

	@Override
	public byte[] copy() {
		return Arrays.copyOfRange(array, offset, offset + length);
	}

	@Override
	public byte[] copyArrayRegion(int offset, int length) {
		offset += this.offset;
		ByteRegion.checkRange(offset, length, this.offset, this.length);
		return Arrays.copyOfRange(array, offset, length);
	}

	@Override
	public byte get(int index) {
		index += this.offset;
		ByteRegion.checkRange(index, 1, this.offset, this.length);
		return array[index];
	}

//	public int indexOf(int startindex, byte b) {
//		int end = this.offset + length;
//		if (startindex < offset || startindex >= end) {
//			throw new IndexOutOfBoundsException(startindex + " is not in bounds of " + offset + "(" + length + ")");
//		}
//		for (int i = startindex; i < end; i++) {
//			if (array[i] == b) {
//				return i;
//			}
//		}
//		return -1;
//	}
//
//	public int indexOf(byte b) {
//		for (int i = offset, end = offset + length; i < end; i++) {
//			if (array[i] == b) {
//				return i;
//			}
//		}
//		return -1;
//	}

	public byte[] getTrimmed() {
		if (array.length == length && offset == 0) {
			return array;
		}
		return Arrays.copyOfRange(array, offset, offset + length);
	}

	@Override
	public long writeTo(OutputStream os) throws IOException {
		os.write(array, offset, length);
		return length;
	}

	@Override
	public long writeTo(DataOutput out) throws IOException {
		out.write(array, offset, length);
		return length;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(length);
		out.write(array, offset, length);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		length = in.readInt();
		array = new byte[length];
		in.readFully(array);
	}

	private Object readResolve() {
		//during deserialization, the offset is set to 0, and the length to the actual length of the array
		//therefore the object is replaceable by a full region
		return new FullByteArrayRegion(array);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @return The contents of this byte array region decoded using UTF-8.
	 */
	@Override
	public String toString() {
		return toString(StandardCharsets.UTF_8);
	}

	@Override
	public String toString(Charset charset) {
		return new String(array, offset, length, charset);
	}

}
