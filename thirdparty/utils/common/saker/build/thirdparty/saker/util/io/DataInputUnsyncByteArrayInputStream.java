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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * {@link UnsyncByteArrayInputStream} subclass that also implements {@link DataInput} to read binary formatted data from
 * the buffer.
 * 
 * @see DataOutputUnsyncByteArrayOutputStream
 */
public class DataInputUnsyncByteArrayInputStream extends UnsyncByteArrayInputStream implements DataInputByteSource {

	/**
	 * Creates a new stream that is backed by the argument buffer.
	 * 
	 * @param buf
	 *            The byte buffer.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @see UnsyncByteArrayInputStream#UnsyncByteArrayInputStream(byte[])
	 */
	public DataInputUnsyncByteArrayInputStream(byte[] buf) throws NullPointerException {
		super(buf);
	}

	/**
	 * Creates a new stream that is backed by a region in the argument array.
	 * 
	 * @param buf
	 *            The byte buffer.
	 * @param offset
	 *            The region start offset. (inclusive)
	 * @param length
	 *            The number of bytes that is available for reading from the buffer starting at the offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @see UnsyncByteArrayInputStream#UnsyncByteArrayInputStream(byte[], int, int)
	 */
	public DataInputUnsyncByteArrayInputStream(byte[] buf, int offset, int length) throws NullPointerException {
		super(buf, offset, length);
	}

	/**
	 * Creates a new stream that is backed by the argument byte array region.
	 * 
	 * @param bytes
	 *            The byte array region.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see UnsyncByteArrayInputStream#UnsyncByteArrayInputStream(ByteArrayRegion)
	 */
	public DataInputUnsyncByteArrayInputStream(ByteArrayRegion bytes) throws NullPointerException {
		super(bytes);
	}

	@Override
	public final void readFully(byte[] b) throws EOFException {
		readFully(b, 0, b.length);
	}

	@Override
	public final void readFully(byte[] b, int offset, int length) throws EOFException {
		int avail = endOffset - position;
		if (length > avail) {
			throw new EOFException();
		}
		System.arraycopy(buffer, position, b, offset, length);
		position += length;
	}

	/**
	 * Reads <code>short</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>short</code> is the same as in {@link #readShort()}.
	 * 
	 * @param b
	 *            The <code>short</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(short[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>short</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>short</code> is the same as in {@link #readShort()}.
	 * 
	 * @param b
	 *            The <code>short</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(short[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length * Short.BYTES > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = (short) (((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF));
			pos += Short.BYTES;
		}
		position = pos;
	}

	/**
	 * Reads <code>int</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>int</code> is the same as in {@link #readInt()}.
	 * 
	 * @param b
	 *            The <code>int</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(int[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>int</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>int</code> is the same as in {@link #readInt()}.
	 * 
	 * @param b
	 *            The <code>int</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(int[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length * Integer.BYTES > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = ((buf[pos]) << 24) | //
					((buf[pos + 1] & 0xFF) << 16) | //
					((buf[pos + 2] & 0xFF) << 8) | //
					(buf[pos + 3] & 0xFF);
			pos += Integer.BYTES;
		}
		position = pos;
	}

	/**
	 * Reads <code>long</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>long</code> is the same as in {@link #readLong()}.
	 * 
	 * @param b
	 *            The <code>long</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(long[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>long</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>long</code> is the same as in {@link #readLong()}.
	 * 
	 * @param b
	 *            The <code>long</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(long[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length * Long.BYTES > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = (((long) buf[pos]) << 56) | //
					((long) (buf[pos + 1] & 0xFF) << 48) | //
					((long) (buf[pos + 2] & 0xFF) << 40) | //
					((long) (buf[pos + 3] & 0xFF) << 32) | //
					((long) (buf[pos + 4] & 0xFF) << 24) | //
					((long) (buf[pos + 5] & 0xFF) << 16) | //
					((long) (buf[pos + 6] & 0xFF) << 8) | //
					(buf[pos + 7] & 0xFF);
			pos += Long.BYTES;
		}
		position = pos;
	}

	/**
	 * Reads <code>float</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>float</code> is the same as in {@link #readFloat()}.
	 * 
	 * @param b
	 *            The <code>float</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(float[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>float</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>float</code> is the same as in {@link #readFloat()}.
	 * 
	 * @param b
	 *            The <code>float</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(float[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length * Float.BYTES > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = Float.intBitsToFloat(((buf[pos]) << 24) | //
					((buf[pos + 1] & 0xFF) << 16) | //
					((buf[pos + 2] & 0xFF) << 8) | //
					(buf[pos + 3] & 0xFF));
			pos += Float.BYTES;
		}
		position = pos;
	}

	/**
	 * Reads <code>double</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>double</code> is the same as in {@link #readDouble()}.
	 * 
	 * @param b
	 *            The <code>double</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(double[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>double</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>double</code> is the same as in {@link #readDouble()}.
	 * 
	 * @param b
	 *            The <code>double</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(double[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length * Double.BYTES > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = Double.longBitsToDouble((((long) buf[pos]) << 56) | //
					((long) (buf[pos + 1] & 0xFF) << 48) | //
					((long) (buf[pos + 2] & 0xFF) << 40) | //
					((long) (buf[pos + 3] & 0xFF) << 32) | //
					((long) (buf[pos + 4] & 0xFF) << 24) | //
					((long) (buf[pos + 5] & 0xFF) << 16) | //
					((long) (buf[pos + 6] & 0xFF) << 8) | //
					(buf[pos + 7] & 0xFF));
			pos += Double.BYTES;
		}
		position = pos;
	}

	/**
	 * Reads <code>char</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>char</code> is the same as in {@link #readChar()}.
	 * 
	 * @param b
	 *            The <code>char</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(char[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>char</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>char</code> is the same as in {@link #readChar()}.
	 * 
	 * @param b
	 *            The <code>char</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(char[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length * Character.BYTES > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = (char) (((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF));
			pos += Character.BYTES;
		}
		position = pos;
	}

	/**
	 * Reads <code>boolean</code>s from the stream and stores them in the argument array.
	 * <p>
	 * The argument array will be filled during the reading. An exception is thrown if there are not enough data to fill
	 * the array.
	 * <p>
	 * The format of each <code>boolean</code> is the same as in {@link #readBoolean()}.
	 * 
	 * @param b
	 *            The <code>boolean</code> array to fill.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public final void readFully(boolean[] b) throws EOFException, NullPointerException {
		readFully(b, 0, b.length);
	}

	/**
	 * Reads <code>boolean</code>s from the stream and stores them in the argument array range.
	 * <p>
	 * The range in the array will be filled during the reading. An exception is thrown if there are not enough data to
	 * fill the range.
	 * <p>
	 * The format of each <code>boolean</code> is the same as in {@link #readBoolean()}.
	 * 
	 * @param b
	 *            The <code>boolean</code> array to fill.
	 * @param offset
	 *            The starting index of the range. (inclusive)
	 * @param length
	 *            The number of elements to read.
	 * @throws EOFException
	 *             If there are not enough data in the stream to fill the buffer.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not within the array.
	 */
	public final void readFully(boolean[] b, int offset, int length)
			throws EOFException, NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(b, offset, length);
		int pos = position;
		int avail = endOffset - pos;
		if (length > avail) {
			throw new EOFException();
		}
		byte[] buf = buffer;
		for (int i = offset, endidx = offset + length; i < endidx; i++) {
			b[i] = buf[pos] != 0;
			++pos;
		}
		position = pos;
	}

	@Override
	public final int skipBytes(int n) {
		if (n < 0) {
			return 0;
		}
		n = Math.min(n, available());
		position += n;
		return n;
	}

	@Override
	public final boolean readBoolean() throws EOFException {
		int b = read();
		if (b < 0) {
			throw new EOFException();
		}
		return b != 0;
	}

	@Override
	public final byte readByte() throws EOFException {
		if (position < endOffset) {
			return buffer[position++];
		}
		throw new EOFException();
	}

	@Override
	public final int readUnsignedByte() throws EOFException {
		return readByte() & 0xFF;
	}

	@Override
	public final short readShort() throws EOFException {
		int pos = position;
		int avail = endOffset - pos;
		if (avail < Short.BYTES) {
			throw new EOFException();
		}

		byte[] buf = buffer;
		short result = SerialUtils.readShortFromBuffer(buf, pos);
		position += Short.BYTES;
		return result;
	}

	@Override
	public final int readUnsignedShort() throws EOFException {
		return readShort() & 0xFFFF;
	}

	@Override
	public final char readChar() throws EOFException {
		int pos = position;
		int avail = endOffset - pos;
		if (avail < Character.BYTES) {
			throw new EOFException();
		}

		byte[] buf = buffer;
		char result = SerialUtils.readCharFromBuffer(buf, pos);
		position += Character.BYTES;
		return result;
	}

	@Override
	public final int readInt() throws EOFException {
		int result = peekInt();
		position += Integer.BYTES;
		return result;
	}

	//XXX more peek methods

	/**
	 * Reads an <code>int</code> from the stream without incrementing its position.
	 * 
	 * @return The read <code>int</code>.
	 * @throws EOFException
	 *             If there are not enough data to read an <code>int</code>.
	 */
	public final int peekInt() throws EOFException {
		int pos = position;
		int avail = endOffset - pos;
		if (avail < Integer.BYTES) {
			throw new EOFException();
		}

		byte[] buf = buffer;
		int result = SerialUtils.readIntFromBuffer(buf, pos);
		return result;
	}

	@Override
	public final long readLong() throws EOFException {
		int pos = position;
		int avail = endOffset - pos;
		if (avail < Long.BYTES) {
			throw new EOFException();
		}

		byte[] buf = buffer;
		long result = SerialUtils.readLongFromBuffer(buf, pos);
		position += Long.BYTES;
		return result;
	}

	@Override
	public final float readFloat() throws EOFException {
		return Float.intBitsToFloat(readInt());
	}

	@Override
	public final double readDouble() throws EOFException {
		return Double.longBitsToDouble(readLong());
	}

	@Override
	public final String readLine() {
		int pos = position;
		int cnt = endOffset;
		int avail = cnt - pos;
		if (avail == 0) {
			return null;
		}
		byte[] buf = buffer;
		StringBuilder sb = new StringBuilder();
		for (int i = pos; i < cnt; i++) {
			char c = (char) (buf[i] & 0xFF);
			switch (c) {
				case '\n': {
					this.position = i + 1;
					return sb.toString();
				}
				case '\r': {
					//peek one, if it is \n, read it, else dont
					if (i + 1 < cnt) {
						char nc = (char) (buf[i + 1] & 0xFF);
						if (nc == '\n') {
							this.position = i + 2;
							return sb.toString();
						}
					}
					this.position = i + 1;
					return sb.toString();
				}
				default: {
					sb.append(c);
					break;
				}
			}
		}
		this.position = cnt;
		return sb.toString();
	}

	@Override
	public final String readUTF() throws IOException {
		return DataInputStream.readUTF(this);
	}

	/**
	 * Reads an <code>int</code> length and the respective number of <code>char</code>s for a string.
	 * <p>
	 * This method reads the length of a char sequence, and length number of characters after that.
	 * 
	 * @return The constructed string from the read characters.
	 * @throws EOFException
	 *             If there are no more input available to complete the operation.
	 * @see DataOutputUnsyncByteArrayOutputStream#writeStringLengthChars(CharSequence)
	 */
	public final String readStringLengthChars() throws EOFException {
		int len = readInt();
		int pos = position;
		int avail = endOffset - pos;
		if (avail < len * Character.BYTES) {
			throw new EOFException();
		}
		char[] chars = new char[len];
		byte[] buf = buffer;
		for (int i = 0; i < len; i++) {
			chars[i] = (char) (((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF));
			pos += 2;
		}
		position += len * Character.BYTES;
		return new String(chars);
	}

	/**
	 * Static implementation for {@link #readStringLengthChars()}, that reads the string from a plain {@link DataInput}.
	 * 
	 * @param in
	 *            The data input.
	 * @return The constructed string from the read characters.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static String readStringLengthChars(DataInput in) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int len = in.readInt();
		char[] chars = new char[len];
		for (int i = 0; i < len; i++) {
			chars[i] = in.readChar();
		}
		return new String(chars);
	}
}
