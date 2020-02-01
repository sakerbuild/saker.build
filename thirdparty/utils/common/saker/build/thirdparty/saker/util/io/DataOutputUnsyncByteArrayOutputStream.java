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
import java.io.UTFDataFormatException;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * {@link UnsyncByteArrayOutputStream} subclass that also implements {@link DataOutput} to write binary formatted data
 * to the output buffer.
 * 
 * @see DataInputUnsyncByteArrayInputStream
 */
public class DataOutputUnsyncByteArrayOutputStream extends UnsyncByteArrayOutputStream implements DataOutputByteSink {

	/**
	 * Creates a new instance with the default buffer size.
	 * 
	 * @see UnsyncByteArrayOutputStream#UnsyncByteArrayOutputStream()
	 */
	public DataOutputUnsyncByteArrayOutputStream() {
		super();
	}

	/**
	 * Creates a new instance with the argument buffer size.
	 * 
	 * @param size
	 *            The size.
	 * @throws NegativeArraySizeException
	 *             If the size is negative.
	 * @see UnsyncByteArrayOutputStream#UnsyncByteArrayOutputStream(int)
	 */
	public DataOutputUnsyncByteArrayOutputStream(int size) throws NegativeArraySizeException {
		super(size);
	}

	/**
	 * Creates a new instance that is directly backed by the argument buffer, and currently contains <code>count</code>
	 * number of valid bytes.
	 * <p>
	 * If more bytes are written to the stream, it may grow accordingly.
	 * 
	 * @param buf
	 *            The backing buffer.
	 * @param count
	 *            The number of valid bytes in the buffer.
	 * @throws IllegalArgumentException
	 *             If the count is negative, or greater than the buffer length.
	 * @see UnsyncByteArrayOutputStream#UnsyncByteArrayOutputStream(byte[], int)
	 */
	public DataOutputUnsyncByteArrayOutputStream(byte[] buf, int count) throws IllegalArgumentException {
		super(buf, count);
	}

	@Override
	public final void writeInt(int v) {
		int cnt = count;
		ensureCapacity(cnt + Integer.BYTES);
		SerialUtils.writeIntToBuffer(v, buf, cnt);
		count += Integer.BYTES;
	}

	@Override
	public final void writeLong(long v) {
		int cnt = count;
		ensureCapacity(cnt + Long.BYTES);
		byte[] b = buf;
		SerialUtils.writeLongToBuffer(v, b, cnt);
		count += Long.BYTES;
	}

	@Override
	public final void writeFloat(float v) {
		writeInt(Float.floatToIntBits(v));
	}

	@Override
	public final void writeDouble(double v) {
		writeLong(Double.doubleToLongBits(v));
	}

	@Override
	public final void writeBoolean(boolean v) {
		write(v ? 1 : 0);
	}

	@Override
	public final void writeByte(int v) {
		write(v);
	}

	@Override
	public final void writeShort(int v) {
		writeShort((short) v);
	}

	@Override
	public final void writeChar(int v) {
		writeChar((char) v);
	}

	@Override
	public final void writeBytes(String s) {
		int cnt = count;
		int len = s.length();
		ensureCapacity(cnt + len);
		byte[] b = buf;
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			b[cnt++] = (byte) c;
		}
		this.count = cnt;
	}

	/**
	 * Writes the characters in the argument sequence to the stream.
	 * <p>
	 * Unline {@link #writeStringLengthChars(CharSequence)}, the length of the character sequence is not written to the
	 * stream, only the characters in the same format as {@link #writeChar(char)}.
	 * 
	 * @param s
	 *            The char sequence to write.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public final void writeChars(CharSequence s) throws NullPointerException {
		Objects.requireNonNull(s, "chars");
		int cnt = count;
		int len = s.length();
		ensureCapacity(cnt + len * Character.BYTES);
		byte[] b = buf;
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			b[cnt] = (byte) (c >>> 8);
			b[cnt + 1] = (byte) (c);
			cnt += Character.BYTES;
		}
		this.count = cnt;
	}

	@Override
	public final void writeChars(String s) {
		writeChars((CharSequence) s);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws UTFDataFormatException
	 *             If the encoded string is longer than 65536 bytes.
	 */
	@Override
	public final void writeUTF(String str) throws UTFDataFormatException {
		// code is based on java.io.DataOutputStream
		//     that writeUTF method really should be public like in java.io.DataInputStream

		int strlen = str.length();

		int cnt = this.count;

		//allocate enough space for len + encoded data
		ensureCapacity(cnt + 2 + strlen * 3);

		byte[] b = this.buf;
		int lenoffset = cnt;

		cnt += 2;

		int utflen = 0;

		int i;
		for (i = 0; i < strlen; i++) {
			char c = str.charAt(i);
			if (!((c >= 0x0001) && (c <= 0x007F)))
				break;
			b[cnt++] = (byte) c;
			++utflen;
		}
		for (; i < strlen; i++) {
			char c = str.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				b[cnt++] = (byte) c;
				++utflen;
			} else if (c > 0x07FF) {
				b[cnt++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				b[cnt++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				b[cnt++] = (byte) (0x80 | ((c >> 0) & 0x3F));
				utflen += 3;
			} else {
				b[cnt++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
				b[cnt++] = (byte) (0x80 | ((c >> 0) & 0x3F));
				utflen += 2;
			}
		}

		if (utflen > 0xFFFF) {
			throw new UTFDataFormatException("encoded string too long: " + utflen + " bytes");
		}

		b[lenoffset++] = (byte) ((utflen >>> 8));
		b[lenoffset] = (byte) ((utflen));

		this.count = cnt;
	}

	/**
	 * Writes the number of characters and the actual characters to the stream.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * writeInt(s.length());
	 * writeChars(s);
	 * </pre>
	 * 
	 * @param s
	 *            The character sequence.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see DataInputUnsyncByteArrayInputStream#readStringLengthChars()
	 */
	public final void writeStringLengthChars(CharSequence s) throws NullPointerException {
		Objects.requireNonNull(s, "char sequence");
		writeInt(s.length());
		writeChars(s);
	}

	//XXX more replace functions

	/**
	 * Replaces an <code>int</code> in the stream at the given offset.
	 * <p>
	 * The capacity and the size of the stream is modified accordingly, so the offset will always point to a valid
	 * region. The buffer size and the valid number of bytes will be the maximum of the current size, and offset + 4.
	 * <p>
	 * The replaced <code>int</code> is in the same format as {@link #writeInt(int)}.
	 * 
	 * @param v
	 *            The <code>int</code> to write at the given offset.
	 * @param offset
	 *            The offset at which the underlying byte array should be modified.
	 */
	public final void replaceInt(int v, int offset) {
		ensureCapacity(offset + Integer.BYTES);
		byte[] b = buf;
		SerialUtils.writeIntToBuffer(v, b, offset);
		count = Math.max(count, offset + Integer.BYTES);
	}

	/**
	 * Same as {@link #writeShort(int)}, but takes a <code>short</code> parameter.
	 * 
	 * @param v
	 *            The value to write.
	 */
	public final void writeShort(short v) {
		int cnt = count;
		ensureCapacity(cnt + Short.BYTES);
		byte[] b = buf;
		b[cnt] = (byte) ((v >>> 8));
		b[cnt + 1] = (byte) ((v));
		count += Short.BYTES;
	}

	/**
	 * Same as {@link #writeChar(int)}, but takes a <code>char</code> parameter.
	 * 
	 * @param v
	 *            The value to write.
	 */
	public final void writeChar(char v) {
		int cnt = count;
		ensureCapacity(cnt + Character.BYTES);
		byte[] b = buf;
		SerialUtils.writeCharToBuffer(v, b, cnt);
		count += Character.BYTES;
	}

	/**
	 * Writes an array of <code>char</code>s to the stream.
	 * <p>
	 * The <code>char</code>s will be written in the same format as {@link #writeChar(char)}.
	 * 
	 * @param b
	 *            The array of <code>char</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(char[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>char</code>s from the specified array to the stream.
	 * <p>
	 * The <code>char</code>s will be written in the same format as {@link #writeChar(char)}.
	 * 
	 * @param b
	 *            The array of <code>char</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void write(char[] b, int offset, int length) throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length * Character.BYTES);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			char c = b[i];
			SerialUtils.writeCharToBuffer(c, bb, cnt);
			cnt += Character.BYTES;
		}
		count = cnt;
	}

	/**
	 * Writes an array of <code>short</code>s to the stream.
	 * <p>
	 * The <code>short</code>s will be written in the same format as {@link #writeShort(short)}.
	 * 
	 * @param b
	 *            The array of <code>short</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(short[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>short</code>s from the specified array to the stream.
	 * <p>
	 * The <code>short</code>s will be written in the same format as {@link #writeShort(short)}.
	 * 
	 * @param b
	 *            The array of <code>short</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */

	public final void write(short[] b, int offset, int length) throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length * Short.BYTES);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			short c = b[i];
			bb[cnt] = (byte) ((c >>> 8));
			bb[cnt + 1] = (byte) ((c));
			cnt += Short.BYTES;
		}
		count = cnt;
	}

	/**
	 * Writes an array of <code>int</code>s to the stream.
	 * <p>
	 * The <code>int</code>s will be written in the same format as {@link #writeInt(int)}.
	 * 
	 * @param b
	 *            The array of <code>int</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(int[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>int</code>s from the specified array to the stream.
	 * <p>
	 * The <code>int</code>s will be written in the same format as {@link #writeInt(int)}.
	 * 
	 * @param b
	 *            The array of <code>int</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void write(int[] b, int offset, int length) throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length * Integer.BYTES);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			int c = b[i];
			SerialUtils.writeIntToBuffer(c, bb, cnt);
			cnt += Integer.BYTES;
		}
		count = cnt;
	}

	/**
	 * Writes an array of <code>long</code>s to the stream.
	 * <p>
	 * The <code>long</code>s will be written in the same format as {@link #writeLong(long)}.
	 * 
	 * @param b
	 *            The array of <code>long</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(long[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>long</code>s from the specified array to the stream.
	 * <p>
	 * The <code>long</code>s will be written in the same format as {@link #writeLong(long)}.
	 * 
	 * @param b
	 *            The array of <code>long</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void write(long[] b, int offset, int length) throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length * Long.BYTES);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			long c = b[i];
			SerialUtils.writeLongToBuffer(c, bb, cnt);
			cnt += Long.BYTES;
		}
		count = cnt;
	}

	/**
	 * Writes an array of <code>float</code>s to the stream.
	 * <p>
	 * The <code>float</code>s will be written in the same format as {@link #writeFloat(float)}.
	 * 
	 * @param b
	 *            The array of <code>float</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(float[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>float</code>s from the specified array to the stream.
	 * <p>
	 * The <code>float</code>s will be written in the same format as {@link #writeFloat(float)}.
	 * 
	 * @param b
	 *            The array of <code>float</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void write(float[] b, int offset, int length) throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length * Float.BYTES);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			int c = Float.floatToIntBits(b[i]);
			SerialUtils.writeIntToBuffer(c, bb, cnt);
			cnt += Float.BYTES;
		}
		count = cnt;
	}

	/**
	 * Writes an array of <code>double</code>s to the stream.
	 * <p>
	 * The <code>double</code>s will be written in the same format as {@link #writeDouble(double)}.
	 * 
	 * @param b
	 *            The array of <code>double</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(double[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>double</code>s from the specified array to the stream.
	 * <p>
	 * The <code>double</code>s will be written in the same format as {@link #writeDouble(double)}.
	 * 
	 * @param b
	 *            The array of <code>double</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void write(double[] b, int offset, int length) throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length * Double.BYTES);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			long c = Double.doubleToLongBits(b[i]);
			SerialUtils.writeLongToBuffer(c, bb, cnt);
			cnt += Double.BYTES;
		}
		count = cnt;
	}

	/**
	 * Writes an array of <code>boolean</code>s to the stream.
	 * <p>
	 * The <code>boolean</code>s will be written in the same format as {@link #writeBoolean(boolean)}.
	 * 
	 * @param b
	 *            The array of <code>boolean</code>s.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void write(boolean[] b) throws NullPointerException {
		Objects.requireNonNull(b, "array");
		write(b, 0, b.length);
	}

	/**
	 * Writes a range of <code>boolean</code>s from the specified array to the stream.
	 * <p>
	 * The <code>boolean</code>s will be written in the same format as {@link #writeBoolean(boolean)}.
	 * 
	 * @param b
	 *            The array of <code>boolean</code>s.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void write(boolean[] b, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int cnt = count;
		ensureCapacity(cnt + length);
		byte[] bb = buf;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			boolean c = b[i];
			bb[cnt] = (byte) (c ? 1 : 0);
			cnt += 1;
		}
		count = cnt;
	}
}
