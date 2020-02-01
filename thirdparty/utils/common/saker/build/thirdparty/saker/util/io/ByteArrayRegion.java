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
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * {@link ByteRegion} implementation backed by a byte array.
 * <p>
 * This is the de-facto implementation of the interface {@link ByteRegion}. Users who wish to create an instance of
 * {@link ByteRegion} should use the static factory methods in this class.
 * <p>
 * Clients who are dealing with {@link ByteRegion} instances may check for them if they're an instance of
 * {@link ByteArrayRegion}, and use the backing {@linkplain #getArray() byte array} directly to improve reading and
 * writing performance. This instance of checking works through RMI connections.
 * <p>
 * The array backed by instances of this class is defined by an {@linkplain #getOffset() offset}. The actual byte region
 * starts at the mentioned offset index, and clients should only access the region defined by it and the
 * {@linkplain #getLength() length}.
 * <p>
 * Methods of this class accept the index parameter relative to the aforementioned offset, i.e. index 0 is the same as
 * accessing the byte at the starting offset.
 * <p>
 * This class can't be subclassed by clients, and they should use the static factory methods to retrieve an instance.
 */
public abstract class ByteArrayRegion implements Externalizable, ByteRegion {
	private static final long serialVersionUID = 1L;
	/**
	 * Singleton instance for an empty {@link ByteArrayRegion}.
	 */
	public static final ByteArrayRegion EMPTY = new EmptyByteArrayRegion();

	/*default*/ ByteArrayRegion() {
	}

	/**
	 * Allocates a new byte array with the given length and returns it as a {@link ByteArrayRegion}.
	 * <p>
	 * This is the same as calling:
	 * 
	 * <pre>
	 * ByteArrayRegion.wrap(new byte[length]);
	 * </pre>
	 * 
	 * Callers shouldn't rely on the identity of the returned {@linkplain #getArray() byte array}. This method may
	 * optimize the allocation for a 0 length byte array, and return a shared object.
	 * 
	 * @param length
	 *            The length of the allocated byte array.
	 * @return The byte array region allocated for the given length.
	 * @throws NegativeArraySizeException
	 *             If the argument is negative.
	 */
	public static ByteArrayRegion allocate(int length) throws NegativeArraySizeException {
		if (length == 0) {
			return EMPTY;
		}
		return wrap(new byte[length]);
	}

	/**
	 * Gets a byte array region that wraps the argument array.
	 * <p>
	 * The returned region will contain the whole array argument.
	 * <p>
	 * If the array is empty, the method will return {@link #EMPTY}, and not a new instance.
	 * 
	 * @param array
	 *            The array.
	 * @return A byte array region wrapping the array.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static ByteArrayRegion wrap(byte[] array) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		if (array.length == 0) {
			return EMPTY;
		}
		return new FullByteArrayRegion(array);
	}

	/**
	 * Gets a byte array region that wraps the argument range of the specified array.
	 * <p>
	 * If the array is empty, the method will return {@link #EMPTY}, and not a new instance.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The offset where the region starts.
	 * @param len
	 *            The length of the region.
	 * @return A byte array region wrapping the array for the given range.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is not in the bounds of the array.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static ByteArrayRegion wrap(byte[] array, int offset, int len)
			throws IndexOutOfBoundsException, NullPointerException {
		ArrayUtils.requireArrayRange(array, offset, len);
		if (len == 0) {
			return EMPTY;
		}
		if (offset == 0 && len == array.length) {
			return new FullByteArrayRegion(array);
		}
		return new OffsetLengthByteArrayRegion(array, offset, len);
	}

	/**
	 * Checks if this region contains any bytes.
	 * 
	 * @return <code>true</code> if the region is empty.
	 */
	public boolean isEmpty() {
		return getLength() == 0;
	}

	/**
	 * Gets the underlying array for this instance.
	 * <p>
	 * The array should only be accessed in the range defined for this instance.
	 * 
	 * @return The underlying array.
	 * @see #getOffset()
	 * @see #getLength()
	 */
	public abstract byte[] getArray();

	/**
	 * Gets an array that contains the full range of this region.
	 * <p>
	 * This method is similar to {@link #copy()}, but if the range is defined to contain the whole underlying array,
	 * then no copies are made.
	 * <p>
	 * The modifications made to the returned array may or may not modify the actually represented byte region.
	 * <p>
	 * The returned array will have a length of {@link #getLength()}.
	 * 
	 * @return The array containing the contents of this byte region.
	 */
	public abstract byte[] copyOptionally();

	/**
	 * Gets the offset of the range for this instance.
	 * 
	 * @return The starting offset of the represented region.
	 */
	public abstract int getOffset();

	/**
	 * Puts an array of bytes into this region starting at the given index.
	 * <p>
	 * The index is 0 based, where the index 0 means that the first byte will be written at the {@linkplain #getOffset()
	 * offset} in the underlying array.
	 * <p>
	 * The full contents of the argument array will be copied into the underlying array for this byte array region.
	 * <p>
	 * This method is the same as calling:
	 * 
	 * <pre>
	 * put(index, bytes, 0, bytes.length);
	 * </pre>
	 * 
	 * @param index
	 *            The index where to start writing the bytes.
	 * @param bytes
	 *            The bytes to write to this region.
	 * @throws IndexOutOfBoundsException
	 *             If the index or any of the written bytes would be out of range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public void put(int index, byte[] bytes) throws IndexOutOfBoundsException, NullPointerException {
		put(index, bytes, 0, bytes.length);
	}

	/**
	 * Puts an array range of bytes into this region starting at the given index.
	 * <p>
	 * The index is 0 based, where the index 0 means that the first byte will be written at the {@linkplain #getOffset()
	 * offset} in the underlying array.
	 * <p>
	 * The contents of the argument array in the given range will be copied into the underlying array for this byte
	 * array region.
	 * 
	 * @param index
	 *            The index where to start writing the bytes.
	 * @param bytes
	 *            The array of bytes to write.
	 * @param offset
	 *            The starting offset in the argument array to start the copying from.
	 * @param len
	 *            The number of bytes to copy from the argument array.
	 * @throws IndexOutOfBoundsException
	 *             If the index or the specified range or any of the written bytes would be out of range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public abstract void put(int index, byte[] bytes, int offset, int len)
			throws IndexOutOfBoundsException, NullPointerException;

	/**
	 * Writes the contents of this byte array region to the argument output stream.
	 * 
	 * @param os
	 *            The output stream to write the contents of this region.
	 * @return The number of bytes written to the output stream. This is the same as {@link #getLength()}.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public abstract long writeTo(OutputStream os) throws IOException;

	/**
	 * Writes the contents of this byte array region to the argument data output.
	 * 
	 * @param out
	 *            The data output to write the contents of this region.
	 * @return The number of bytes written to the data output. This is the same as {@link #getLength()}.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public abstract long writeTo(DataOutput out) throws IOException;

	/**
	 * Checks if the byte contents defined by this region equals to the contents of the argument region.
	 * 
	 * @param region
	 *            The region to compare.
	 * @return <code>true</code> if the bytes are the same in the byte regions defined by <code>this</code> and the
	 *             argument.
	 */
	public boolean regionEquals(ByteArrayRegion region) {
		int tlen = this.getLength();
		int rlen = region.getLength();
		if (tlen != rlen) {
			return false;
		}
		byte[] tarray = getArray();
		int toffset = getOffset();
		byte[] rarray = region.getArray();
		int roffset = region.getOffset();
		return ArrayUtils.regionEquals(tarray, toffset, rarray, roffset, tlen);
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

	/**
	 * Returns a string representation of this byte region by decoding its contents using the given charset.
	 * 
	 * @param charset
	 *            The charset to use to decode the bytes.
	 * @return The decoded string representation of the byte region.
	 * @throws NullPointerException
	 *             If the charset is <code>null</code>.
	 */
	public String toString(Charset charset) throws NullPointerException {
		return new String(getArray(), getOffset(), getLength(), charset);
	}
}
