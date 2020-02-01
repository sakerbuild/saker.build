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

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * Class similar to {@link ByteArrayOutputStream}, but the methods are not sychronized.
 * <p>
 * The class also contains more functions for more complex manipulations.
 * <p>
 * For writing structured data to the stream, use the subclass {@link DataOutputUnsyncByteArrayOutputStream}.
 * 
 * @see UnsyncByteArrayInputStream
 */
public class UnsyncByteArrayOutputStream extends OutputStream implements ByteSink {
	/**
	 * The default buffer size to use for constructing new instances.
	 */
	private static final int DEFAULT_BUFFER_SIZE = 128;
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	/**
	 * The buffer that holds the bytes.
	 */
	protected byte[] buf;
	/**
	 * The number of valid data in the buffer.
	 * <p>
	 * This means that the region <code>0..count</code> contains valid bytes.
	 */
	protected int count;

	/**
	 * Creates a new instance with the default buffer size of {@value #DEFAULT_BUFFER_SIZE}.
	 */
	public UnsyncByteArrayOutputStream() {
		this(DEFAULT_BUFFER_SIZE);
	}

	/**
	 * Creates a new instance with the argument buffer size.
	 * 
	 * @param size
	 *            The size.
	 * @throws NegativeArraySizeException
	 *             If the size is negative.
	 */
	public UnsyncByteArrayOutputStream(int size) throws NegativeArraySizeException {
		this.buf = new byte[size];
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
	 */
	public UnsyncByteArrayOutputStream(byte[] buf, int count) throws IllegalArgumentException {
		if (count < 0) {
			throw new IllegalArgumentException("Count is negative. (" + count + ")");
		}
		if (count > buf.length) {
			throw new IllegalArgumentException(
					"Count (" + count + ") greater than buffer length (" + buf.length + ").");
		}
		this.buf = buf;
		this.count = count;
	}

	/**
	 * Grows the internal buffer of the stream to be able to hold the specified amount of bytes.
	 * 
	 * @param mincapacity
	 *            The minimum bytes to be able to hold.
	 */
	public void ensureCapacity(int mincapacity) {
		// overflow-conscious code
		if (mincapacity - buf.length > 0) {
			grow(mincapacity);
		}
	}

	private void grow(int mincapacity) {
		// overflow-conscious code
		int oldcapacity = buf.length;
		int newcapacity = oldcapacity << 1;
		if (newcapacity - mincapacity < 0) {
			newcapacity = mincapacity;
		}
		if (newcapacity - MAX_ARRAY_SIZE > 0) {
			newcapacity = hugeCapacity(mincapacity);
		}
		buf = Arrays.copyOf(buf, newcapacity);
	}

	private static int hugeCapacity(int mincapacity) {
		if (mincapacity < 0) {
			// overflow
			throw new OutOfMemoryError();
		}
		return (mincapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	@Override
	public void write(ByteArrayRegion buf) {
		try {
			buf.writeTo((OutputStream) this);
		} catch (IOException e) {
			//we don't ever throw an IOException
			throw new AssertionError(e);
		}
	}

	@Override
	public long readFrom(ByteSource in) throws IOException {
		Objects.requireNonNull(in, "in");
		ensureCapacity(4 * 1024);
		long count = 0;
		while (true) {
			int rem = buf.length - this.count;
			if (rem == 0) {
				grow(buf.length * 2);
				rem = buf.length - this.count;
			}
			int read = in.read(ByteArrayRegion.wrap(buf, this.count, rem));
			if (read <= 0) {
				break;
			}
			this.count += read;
			count += read;
		}
		return count;
	}

	/**
	 * Reads bytes from the argument input stream until no more bytes are available.
	 * 
	 * @param in
	 *            The input stream.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public long readFrom(InputStream in) throws IOException, NullPointerException {
		return readFrom(ByteSource.valueOf(in));
	}

	/**
	 * Reads at most the given number of bytes from the input stream.
	 * 
	 * @param in
	 *            The input stream.
	 * @param count
	 *            The maximum number of bytes to read.
	 * @return The actually read number of bytes. This is less than <code>count</code> if there were no more available
	 *             bytes in the input stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input stream is <code>null</code>.
	 */
	public int readFrom(InputStream in, int count) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		ensureCapacity(this.count + count);
		int totalread = 0;
		while (totalread < count) {
			int read = in.read(buf, this.count, count - totalread);
			if (read <= 0) {
				break;
			}
			this.count += read;
			totalread += read;
		}
		return totalread;
	}

	/**
	 * Copies the contets of the argument byte buffer to this output stream.
	 * <p>
	 * The number of bytes copied equals to {@link ByteBuffer#remaining()}.
	 * 
	 * @param buffer
	 *            The byte buffer.
	 * @return The number of bytes copied.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 */
	public int write(ByteBuffer buffer) throws NullPointerException {
		Objects.requireNonNull(buffer, "buffer");
		int remaining = buffer.remaining();
		if (remaining <= 0) {
			return 0;
		}
		ensureCapacity(this.count + remaining);
		buffer.get(this.buf, this.count, remaining);
		this.count += remaining;
		return remaining;
	}

	@Override
	public void write(int b) {
		ensureCapacity(count + Byte.BYTES);
		buf[count] = (byte) b;
		count += Byte.BYTES;
	}

	@Override
	public void write(byte[] b) {
		this.write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) {
		ensureCapacity(count + len);
		System.arraycopy(b, off, buf, count, len);
		count += len;
	}

	/**
	 * Replaces a byte in the stream at the given offset.
	 * <p>
	 * Only the lowest 8 bits of the argument value is used.
	 * <p>
	 * The capacity and the size of the stream is modified accordingly, so the offset will always point to a valid
	 * region. The buffer size and the valid number of bytes will be the maximum of the current size, and offset + 1.
	 * 
	 * @param v
	 *            The value to set to the byte at the given offset.
	 * @param offset
	 *            The offset of the byte to modify.
	 */
	public void replaceByte(int v, int offset) {
		ensureCapacity(offset + Byte.BYTES);
		byte[] b = buf;
		b[offset] = (byte) v;
		count = Math.max(count, offset + Byte.BYTES);
	}

	/**
	 * Appends the same byte data to the stream multiple times.
	 * 
	 * @param b
	 *            The byte value to append to the stream.
	 * @param count
	 *            The number of times the byte should be appended.
	 */
	public void repeat(byte b, int count) {
		int cnt = this.count;
		ensureCapacity(cnt + count);
		Arrays.fill(buf, cnt, cnt + count, b);
		this.count += count;
	}

	/**
	 * Reduces the number of valid bytes in the stream.
	 * <p>
	 * This method discards bytes from the end of the valid bytes region in the internal buffer. The array size of the
	 * internal buffer is unaffected. Only the number of valid bytes are modified in the stream.
	 * <p>
	 * This method can be used to throw away some written bytes from the stream.
	 * 
	 * @param size
	 *            The new number of valid bytes in the stream buffer.
	 * @throws IllegalArgumentException
	 *             If the new size is greater than {@link #size()}.
	 */
	public void reduceSize(int size) throws IllegalArgumentException {
		if (size > count) {
			throw new IllegalArgumentException(
					"New size (" + size + ") must be less than or equal to size (" + count + ").");
		}
		this.count = size;
	}

	/**
	 * Writes the current contents of the stream to the argument.
	 * <p>
	 * This method returns the number of bytes written to the stream to have the method signature be compatible with
	 * {@link ByteSource#writeTo(ByteSink)}.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @return The number of bytes written to the argument. This is the same as {@link #size()}.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public long writeTo(ByteSink out) throws IOException {
		int cnt = count;
		out.write(ByteArrayRegion.wrap(buf, 0, cnt));
		return cnt;
	}

	/**
	 * Writes a region of the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @param offset
	 *            The starting offset of the region to write. (inclusive)
	 * @param length
	 *            The number of bytes to write to the argument.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws IndexOutOfBoundsException
	 *             If <code>offset + len &gt; {@link #size()} || offset &lt; 0</code>.
	 */
	public void writeTo(ByteSink out, int offset, int length) throws IOException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRangeLength(this.count, offset, length);
		out.write(ByteArrayRegion.wrap(buf, offset, length));
	}

	/**
	 * Writes the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void writeTo(DataOutput out) throws IOException {
		out.write(buf, 0, count);
	}

	/**
	 * Writes a region of the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @param offset
	 *            The starting offset of the region to write. (inclusive)
	 * @param length
	 *            The number of bytes to write to the argument.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws IndexOutOfBoundsException
	 *             If <code>offset + len &gt; {@link #size()} || offset &lt; 0</code>.
	 */
	public void writeTo(DataOutput out, int offset, int length) throws IOException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRangeLength(this.count, offset, length);
		out.write(buf, offset, length);
	}

	/**
	 * Writes the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void writeTo(OutputStream out) throws IOException {
		out.write(buf, 0, count);
	}

	/**
	 * Writes a region of the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @param offset
	 *            The starting offset of the region to write. (inclusive)
	 * @param length
	 *            The number of bytes to write to the argument.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws IndexOutOfBoundsException
	 *             If <code>offset + len &gt; {@link #size()} || offset &lt; 0</code>.
	 */
	public void writeTo(OutputStream out, int offset, int length) throws IOException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRangeLength(this.count, offset, length);
		out.write(buf, offset, length);
	}

	/**
	 * Writes the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 */
	public void writeTo(PrintStream out) {
		out.write(buf, 0, count);
	}

	/**
	 * Writes a region of the current contents of the stream to the argument.
	 * 
	 * @param out
	 *            The output to write the contents to.
	 * @param offset
	 *            The starting offset of the region to write. (inclusive)
	 * @param length
	 *            The number of bytes to write to the argument.
	 * @throws IndexOutOfBoundsException
	 *             If <code>offset + len &gt; {@link #size()} || offset &lt; 0</code>.
	 */
	public void writeTo(PrintStream out, int offset, int length) throws IndexOutOfBoundsException {
		ArrayUtils.requireArrayRangeLength(this.count, offset, length);
		out.write(buf, offset, length);
	}

	/**
	 * Resets the stream to hold 0 bytes.
	 * <p>
	 * Any current bytes in the stream are discarded.
	 * <p>
	 * The current data stored in the stream is not overwritten.
	 */
	public void reset() {
		count = 0;
	}

	/**
	 * Gets the currently present data in the stream in a newly allocated array.
	 * <p>
	 * The returned array has the same length as the number of valid bytes in the stream.
	 * 
	 * @return The data array.
	 */
	public byte[] toByteArray() {
		return Arrays.copyOf(buf, count);
	}

	/**
	 * Converts the current data buffer to a {@link ByteArrayRegion}.
	 * <p>
	 * The returned byte array region is backed by the internal buffer of the stream. Any modifications made to the
	 * returned array may (but not necessarily) propagate back to the stream.
	 * <p>
	 * If any operations are called on the stream after this method returns, the modifications might not propagate back,
	 * as the internal buffer may be reallocated.
	 * 
	 * @return The byte array region representing the valid data in the stream.
	 */
	public ByteArrayRegion toByteArrayRegion() {
		return ByteArrayRegion.wrap(buf, 0, count);
	}

	/**
	 * Gets the current number of valid bytes in the stream.
	 * <p>
	 * This method is named <code>size</code> instead of <code>getSize</code>, to be aligned with the
	 * {@link ByteArrayOutputStream#size()} function naming.
	 * 
	 * @return The count of valid bytes.
	 */
	public int size() {
		return count;
	}

	/**
	 * Gets the current maximum number of bytes the stream can hold.
	 * <p>
	 * The capacity of
	 *
	 * @return The capacity.
	 */
	public int getCapacity() {
		return buf.length;
	}

	/**
	 * Checks if the stream currently holds any bytes.
	 * 
	 * @return <code>true</code> if the stream is empty.
	 */
	public boolean isEmpty() {
		return count == 0;
	}

	/**
	 * Gets the current backing buffer of the stream.
	 * <p>
	 * Any modifications made to the elements of the returned array may be propagated back to the stream. No copy is
	 * made.
	 * 
	 * @return The buffer.
	 */
	public byte[] getBuffer() {
		return buf;
	}

	/**
	 * Gets the first occurrence of a byte value in the stream.
	 * 
	 * @param b
	 *            The byte to find.
	 * @return The index of the byte in the stream, or -1 if not found.
	 */
	public int indexOf(byte b) {
		int count = this.count;
		byte[] buf = this.buf;
		for (int i = 0; i < count; i++) {
			if (buf[i] == b) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gets the first occurrence of a byte after a given offset.
	 * 
	 * @param b
	 *            The byte to find.
	 * @param offset
	 *            The starting offset of the search. (inclusive)
	 * @return The index of the byte in the stream, or -1 if not found.
	 */
	public int indexOf(byte b, int offset) {
		int count = this.count;
		if (offset < 0) {
			offset = 0;
		} else if (offset >= count) {
			return -1;
		}
		byte[] buf = this.buf;
		for (int i = offset; i < count; i++) {
			if (buf[i] == b) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gets the last occurrence of a byte in the stream.
	 * 
	 * @param b
	 *            The byte to find.
	 * @return The last index of a byte in the stream, or -1 if not found.
	 */
	public int lastIndexOf(byte b) {
		int count = this.count;
		byte[] buf = this.buf;
		for (int i = count - 1; i >= 0; i--) {
			if (buf[i] == b) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gets the last occurrence of a byte after a given offset.
	 * 
	 * @param b
	 *            The byte to find.
	 * @param offset
	 *            The starting offset of the search. (inclusive)
	 * @return The last index of a byte in the stream, or -1 if not found.
	 * @throws IndexOutOfBoundsException
	 *             If the offset is negative, or greater than {@link #size()}.
	 */
	public int lastIndexOf(byte b, int offset) throws IndexOutOfBoundsException {
		int count = this.count;
		if (offset >= count) {
			return -1;
		}
		if (offset < 0) {
			offset = 0;
		}
		byte[] buf = this.buf;
		for (int i = count - 1; i >= offset; i--) {
			if (buf[i] == b) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Converts the current contents of the stream to a string representation by decoding it using UTF-8.
	 * 
	 * @return The decoded string.
	 */
	@Override
	public String toString() {
		return toString(StandardCharsets.UTF_8);
	}

	/**
	 * Converts the current contents of the stream to string representation by decoding it using the argument charset.
	 * 
	 * @param charset
	 *            The charset to use to decode the raw bytes.
	 * @return The string representation.
	 */
	public String toString(Charset charset) {
		return new String(buf, 0, count, charset);
	}

	/**
	 * Converts the specified region of data of the stream to a string representation by decoding it using UTF-8.
	 * 
	 * @param offset
	 *            The starting offset index. (inclusive)
	 * @param length
	 *            The number of bytes to convert.
	 * @return The string representation.
	 * @throws IndexOutOfBoundsException
	 *             If the offset is negative, or greater than {@link #size()}.
	 */
	public String toString(int offset, int length) throws IndexOutOfBoundsException {
		return toString(offset, length, StandardCharsets.UTF_8);
	}

	/**
	 * Converts the specified region of data of the stream to a string representation by decoding it using the argument
	 * charset.
	 * 
	 * @param offset
	 *            The starting offset index. (inclusive)
	 * @param length
	 *            The number of bytes to convert.
	 * @param charset
	 *            The charset to use to decode the raw bytes.
	 * @return The string representation.
	 * @throws IndexOutOfBoundsException
	 *             If the offset is negative, or greater than {@link #size()}.
	 */
	public String toString(int offset, int length, Charset charset) throws IndexOutOfBoundsException {
		ArrayUtils.requireArrayRangeLength(this.count, offset, length);
		return new String(buf, offset, length, charset);
	}

	@Override
	public void close() {
	}

}
