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

import java.nio.ByteBuffer;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIByteRegionWrapper;

/**
 * Interface for representing a view to a byte array.
 * <p>
 * This interface provides methods to access an underlying byte array with a given length. The length of the byte region
 * does not change over the lifetime of it.
 * <p>
 * This interface is similar to the {@link ByteBuffer} class, but defines less functionality and is designed to be RMI
 * compatible.
 * <p>
 * Implementations of this interface may be immutable, in which case any attempt that tries to modify the contents of
 * the underlying array should throw an {@link UnsupportedOperationException}.
 * <p>
 * A simple implementation of this interface is {@link ByteArrayRegion}.
 * <p>
 * Users are generally recommended not to implement this interface directly, but use the classes available in this
 * library. (See {@link ByteArrayRegion} for wrapping preallocated arrays.)
 * <p>
 * When designing interfaces for RMI compatibility, and using {@link ByteRegion} instances, one should use the following
 * guidelines:
 * <ul>
 * <li>If you're reading from an array, use {@link ByteArrayRegion} as the parameter type. In this case the relevant
 * part of the array will be transferred to your endpoint.</li>
 * <li>If you're writing to a byte region, use {@link ByteRegion} as the parameter type and annotate it with
 * <code>{@link RMIWrap &#064;RMIWrap}({@link RMIByteRegionWrapper}.class)</code>. In this case you'll call
 * {@link #put(int, ByteArrayRegion)} method on the argument accordingly, which calls will be forwarded over the RMI
 * connection. Calling {@link #getLength()} on the argument will result in no actual RMI calls, thanks to the annotated
 * wrapper.</li>
 * <li>If you're reading and writing, see the writing use-case. You'll be reading the contents of the byte region by
 * calling one of the copy methods.</li>
 * <li>When writing to an array, you can use <code>instanceof {@link ByteArrayRegion}</code> to improve performance of
 * your method call. Calling and putting the result of your method directly to the underlying array using
 * {@link ByteArrayRegion#getArray()}, can be more efficient than allocating a temporary buffer for writing using the
 * put methods.</li>
 * <li>When returning an array result of your method call consider using {@link ByteArrayRegion} as the result type, as
 * that can allow reducing the number of array allocations. E.g. If you're reading some data into an internal buffer,
 * then by returing {@link ByteArrayRegion}, you don't need to trim the length of the buffer for the return value.</li>
 * </ul>
 * When you're dealing with byte regions, you should only call the writing methods when explicitly asked to. If you
 * wouldn't write contents into an array, you should write contents to a byte region either.
 * <p>
 * Generally, when dealing with different types of {@link ByteRegion} and {@link ByteArrayRegion} in your code, you
 * should prefer using the type {@link ByteArrayRegion} instead of upcasting it to {@link ByteRegion}. This provides
 * extra information about where the array resides, and because {@link ByteRegion} was primarily designed to be used via
 * RMI calls, they should only be used as parameters and maybe return types.
 * 
 * @see ByteArrayRegion
 */
@PublicApi
public interface ByteRegion {
	/**
	 * Copies underlying contents of this byte region and returns it as a byte array.
	 * <p>
	 * Modifying the returned array will have no effect on the contents of this byte region.
	 * 
	 * @return The byte contents of this byte region.
	 */
	public default byte[] copy() {
		return copyArrayRegion(0, getLength());
	}

	/**
	 * Copies contents from a subregion of this byte region and returns it as an array.
	 * <p>
	 * Modifying the returned array will have no effect on the contents of this byte region.
	 * 
	 * @param offset
	 *            The offset to start copying the bytes from.
	 * @param length
	 *            The number of bytes to copy from the byte region.
	 * @return An array of bytes specified by the argument region.
	 * @throws IndexOutOfBoundsException
	 *             If <code>offset &lt; 0 || offset + length &gt; this.length</code>.
	 * @throws IllegalArgumentException
	 *             If <code>length &lt; 0</code>.
	 */
	public byte[] copyArrayRegion(int offset, int length) throws IndexOutOfBoundsException, IllegalArgumentException;

	/**
	 * Gets the byte at the specified index.
	 * 
	 * @param index
	 *            The index to get the byte at.
	 * @return The byte at the given index.
	 * @throws IndexOutOfBoundsException
	 *             If <code>index &lt; 0 || index &gt;= length</code>.
	 */
	public byte get(int index) throws IndexOutOfBoundsException;

	/**
	 * Gets the length of this byte region.
	 * <p>
	 * The length of a region doesn't change over the lifetime of an object.
	 * 
	 * @return The length.
	 */
	@RMICacheResult
	public int getLength();

	/**
	 * Puts the given bytes into the byte region starting at the given offset.
	 * <p>
	 * This method is the same as calling {@link #put(int, byte)} for each byte in the argument region and incrementing
	 * the index after each operation.
	 * 
	 * @param index
	 *            The index to start putting the bytes from.
	 * @param bytes
	 *            The bytes to put into the region.
	 * @throws UnsupportedOperationException
	 *             If the byte region is unmodifiable.
	 * @throws IndexOutOfBoundsException
	 *             If <code>index &lt; 0 || index + bytes.length &gt; length</code>.
	 * @throws NullPointerException
	 *             If the bytes are <code>null</code>.
	 */
	public void put(int index, ByteArrayRegion bytes)
			throws UnsupportedOperationException, IndexOutOfBoundsException, NullPointerException;

	/**
	 * Puts a byte into the region at the given index.
	 * 
	 * @param index
	 *            The index to put the byte to.
	 * @param b
	 *            The byte to write into the region.
	 * @throws UnsupportedOperationException
	 *             If the byte region is unmodifiable.
	 * @throws IndexOutOfBoundsException
	 *             If <code>index &lt; 0 || index &gt;= length</code>.
	 */
	public default void put(int index, byte b) throws UnsupportedOperationException, IndexOutOfBoundsException {
		byte[] buf = { b };
		put(index, ByteArrayRegion.wrap(buf));
	}

	/**
	 * Validation method for method arguments to check if a given region resides in the usage region specified by a
	 * offset-length pair.
	 * <p>
	 * This method throws an exception if the specified region is not fully in the usage region.
	 * 
	 * @param regionindex
	 *            The region start index.
	 * @param regionlength
	 *            The region length.
	 * @param offset
	 *            The offset where the enclosing region starts.
	 * @param length
	 *            The length of the enclosing region.
	 * @throws IndexOutOfBoundsException
	 *             If <code>regionindex &lt; offset || regionindex + regionlength &gt; offset + length</code>.
	 */
	public static void checkRange(int regionindex, int regionlength, int offset, int length)
			throws IndexOutOfBoundsException {
		if (regionindex < offset || regionindex + regionlength > offset + length) {
			throw new IndexOutOfBoundsException(
					regionindex + "(" + regionlength + ") is out of bounds for " + offset + "(" + length + ")");
		}
	}

}
