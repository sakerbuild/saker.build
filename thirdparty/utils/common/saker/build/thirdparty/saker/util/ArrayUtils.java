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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import saker.build.thirdparty.saker.util.ArrayAccessor;

/**
 * Utility class containg functions related to arrays.
 */
public class ArrayUtils {

	/**
	 * Invokes the argument action for every element in the specified array.
	 * <p>
	 * The action is invoked in the same order as the elements occurr in the array.
	 * 
	 * @param array
	 *            The array.
	 * @param action
	 *            The action to invoke for each element.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <T> void forEach(T[] array, Consumer<? super T> action) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		Objects.requireNonNull(action, "action");
		for (T t : array) {
			action.accept(t);
		}
	}

	/**
	 * Clones the array and reverses it.
	 * 
	 * @param array
	 *            The array.
	 * @return The cloned and reversed array or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <T> T[] cloneReverse(T[] array) {
		if (array == null) {
			return null;
		}
		//don't use clone, as we don't need the actual elements to be copied
		@SuppressWarnings("unchecked")
		T[] res = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length);
		for (int i = 0; i < res.length; i++) {
			res[i] = array[res.length - 1 - i];
		}
		return res;
	}

	/**
	 * Reverses the array in place.
	 * 
	 * @param array
	 *            The array to reverse.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static void reverse(Object[] array) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		final int half = array.length / 2;
		final int lenm1 = array.length - 1;
		for (int i = 0; i < half; i++) {
			int idx = lenm1 - i;
			Object tmp = array[idx];
			array[idx] = array[i];
			array[i] = tmp;
		}
	}

	/**
	 * Reverses the specified region of the array in place.
	 * 
	 * @param array
	 *            The array to reverse.
	 * @param start
	 *            The starting index of the reversed region. (inclusive)
	 * @param end
	 *            The end index of the reversed region. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void reverse(Object[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRange(array, start, end);
		final int half = (end - start) / 2;
		final int lenm1 = (end - start) - 1;
		for (int i = 0; i < half; i++) {
			int idx = lenm1 - i;
			Object tmp = array[start + idx];
			array[start + idx] = array[start + i];
			array[start + i] = tmp;
		}
	}

	/**
	 * Converts the array to a string representation and appends it to the argument string builder.
	 * 
	 * @param array
	 *            The array.
	 * @param sb
	 *            The string builder.
	 * @throws NullPointerException
	 *             If the string builder is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 */
	public static void arrayToString(Object array, StringBuilder sb)
			throws NullPointerException, IllegalArgumentException {
		if (array == null) {
			sb.append("null");
			return;
		}
		int len = Array.getLength(array);
		if (len == 0) {
			sb.append("[]");
			return;
		}
		toStringReflectionNonEmptyArrayImpl(array, len, sb);
	}

	/**
	 * Converts the array to a string representation.
	 * 
	 * @param array
	 *            The array.
	 * @return The string representation of the array.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 */
	public static String arrayToString(Object array) throws IllegalArgumentException {
		if (array == null) {
			return "null";
		}
		int len = Array.getLength(array);
		if (len == 0) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder();
		toStringReflectionNonEmptyArrayImpl(array, len, sb);
		return sb.toString();
	}

	/**
	 * Computes the hash code of the argument array.
	 * <p>
	 * The function delegates the array hash code call to the appropriate {@link Arrays#hashCode(byte[])
	 * Arrays.hashCode} overload with the appropriate array type.
	 * <p>
	 * The method doesn't compute a deep hash code, meaning, that if the argument is an object array, and there are
	 * nested arrays in it (i.e. multi dimensional arrays), the element hash codes will not be computed for them. Use
	 * {@link #arrayDeepHashCode(Object)} to compute the deep hash code.
	 * 
	 * @param array
	 *            The array.
	 * @return The computed hash code.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 */
	public static int arrayHashCode(Object array) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(array, "array");
		Class<?> type = array.getClass();
		if (!type.isArray()) {
			throw new IllegalArgumentException("Parameter is not array: " + type);
		}

		if (type == byte[].class) {
			return Arrays.hashCode((byte[]) array);
		}
		if (type == short[].class) {
			return Arrays.hashCode((short[]) array);
		}
		if (type == int[].class) {
			return Arrays.hashCode((int[]) array);
		}
		if (type == long[].class) {
			return Arrays.hashCode((long[]) array);
		}
		if (type == double[].class) {
			return Arrays.hashCode((double[]) array);
		}
		if (type == float[].class) {
			return Arrays.hashCode((float[]) array);
		}
		if (type == boolean[].class) {
			return Arrays.hashCode((boolean[]) array);
		}
		if (type == char[].class) {
			return Arrays.hashCode((char[]) array);
		}
		return Arrays.hashCode((Object[]) array);
	}

	/**
	 * Computes the deep hash code of the argument array.
	 * <p>
	 * The function delegates the array hash code call to the appropriate {@link Arrays#hashCode(byte[])
	 * Arrays.hashCode} overload if the array has a primitive component type, else it is delegated to
	 * {@link Arrays#deepHashCode(Object[])}.
	 * 
	 * @param array
	 *            The array.
	 * @return The computed hash code.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 */
	public static int arrayDeepHashCode(Object array) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(array, "array");
		Class<?> type = array.getClass();
		if (!type.isArray()) {
			throw new IllegalArgumentException("Parameter is not array: " + type);
		}

		if (type == byte[].class) {
			return Arrays.hashCode((byte[]) array);
		}
		if (type == char[].class) {
			return Arrays.hashCode((char[]) array);
		}
		if (type == double[].class) {
			return Arrays.hashCode((double[]) array);
		}
		if (type == float[].class) {
			return Arrays.hashCode((float[]) array);
		}
		if (type == int[].class) {
			return Arrays.hashCode((int[]) array);
		}
		if (type == long[].class) {
			return Arrays.hashCode((long[]) array);
		}
		if (type == short[].class) {
			return Arrays.hashCode((short[]) array);
		}
		if (type == boolean[].class) {
			return Arrays.hashCode((boolean[]) array);
		}
		return Arrays.deepHashCode((Object[]) array);
	}

	/**
	 * Checks the equality of the two argument arrays.
	 * <p>
	 * This method handles primitive array types, and delegates those calls to the appropriate overload of
	 * {@link Arrays#equals(boolean[], boolean[]) Arrays.equals}.
	 * <p>
	 * This method does shallow equals, meaning that any of the arguments contain multi dimensional arrays, this method
	 * doesn't examine their contents. To check deep equality, use {@link #arraysDeepEqual(Object, Object)}.
	 * <p>
	 * If both arguments are <code>null</code>, <code>true</code> is returned. If only one, <code>false</code>.
	 * 
	 * @param array1
	 *            The first array.
	 * @param array2
	 *            The second array.
	 * @return <code>true</code> if the arrays equal.
	 * @throws IllegalArgumentException
	 *             If any of the arguments are not arrays.
	 */
	public static boolean arraysEqual(Object array1, Object array2) throws IllegalArgumentException {
		if (array1 == array2) {
			return true;
		}
		if (array1 == null || array2 == null) {
			return false;
		}
		Class<? extends Object> type = array1.getClass();
		if (!type.isArray()) {
			throw new IllegalArgumentException("First parameter is not array: " + type.getName());
		}
		if (!array2.getClass().isArray()) {
			throw new IllegalArgumentException("Second parameter is not array: " + array2.getClass().getName());
		}
		if (array1 instanceof Object[]) {
			if (!(array2 instanceof Object[])) {
				return false;
			}
			return Arrays.equals((Object[]) array1, (Object[]) array2);
		}
		if (array2 instanceof Object[]) {
			//array1 is not Object[], cannot equal
			return false;
		}
		if (type != array2.getClass()) {
			return false;
		}
		if (type == byte[].class) {
			return Arrays.equals((byte[]) array1, (byte[]) array2);
		}
		if (type == short[].class) {
			return Arrays.equals((short[]) array1, (short[]) array2);
		}
		if (type == int[].class) {
			return Arrays.equals((int[]) array1, (int[]) array2);
		}
		if (type == long[].class) {
			return Arrays.equals((long[]) array1, (long[]) array2);
		}
		if (type == float[].class) {
			return Arrays.equals((float[]) array1, (float[]) array2);
		}
		if (type == double[].class) {
			return Arrays.equals((double[]) array1, (double[]) array2);
		}
		if (type == char[].class) {
			return Arrays.equals((char[]) array1, (char[]) array2);
		}
		return Arrays.equals((boolean[]) array1, (boolean[]) array2);
	}

	/**
	 * Checks the deep equality of the two argument arrays.
	 * <p>
	 * This method handles primitive array types, and delegates those calls to the appropriate overload of
	 * {@link Arrays#equals(boolean[], boolean[]) Arrays.equals}. If the arrays have reference component types,
	 * {@link Arrays#deepEquals(Object[], Object[])} is used to check equality.
	 * <p>
	 * If both arguments are <code>null</code>, <code>true</code> is returned. If only one, <code>false</code>.
	 * 
	 * @param array1
	 *            The first array.
	 * @param array2
	 *            The second array.
	 * @return <code>true</code> if the arrays equal.
	 * @throws IllegalArgumentException
	 *             If any of the arguments are not arrays.
	 */
	public static boolean arraysDeepEqual(Object array1, Object array2) throws IllegalArgumentException {
		if (array1 == array2) {
			return true;
		}
		if (array1 == null || array2 == null) {
			return false;
		}
		Class<? extends Object> type = array1.getClass();
		if (!type.isArray()) {
			throw new IllegalArgumentException("First parameter is not array: " + type.getName());
		}
		if (!array2.getClass().isArray()) {
			throw new IllegalArgumentException("Second parameter is not array: " + array2.getClass().getName());
		}
		if (array1 instanceof Object[]) {
			if (!(array2 instanceof Object[])) {
				return false;
			}
			return Arrays.deepEquals((Object[]) array1, (Object[]) array2);
		}
		if (array2 instanceof Object[]) {
			//array1 is not Object[], cannot equal
			return false;
		}
		if (type != array2.getClass()) {
			return false;
		}
		if (type == byte[].class) {
			return Arrays.equals((byte[]) array1, (byte[]) array2);
		}
		if (type == short[].class) {
			return Arrays.equals((short[]) array1, (short[]) array2);
		}
		if (type == int[].class) {
			return Arrays.equals((int[]) array1, (int[]) array2);
		}
		if (type == long[].class) {
			return Arrays.equals((long[]) array1, (long[]) array2);
		}
		if (type == float[].class) {
			return Arrays.equals((float[]) array1, (float[]) array2);
		}
		if (type == double[].class) {
			return Arrays.equals((double[]) array1, (double[]) array2);
		}
		if (type == char[].class) {
			return Arrays.equals((char[]) array1, (char[]) array2);
		}
		return Arrays.equals((boolean[]) array1, (boolean[]) array2);
	}

	//XXX the range hash code and range equality methods could be specialized to primitive component types as well
	/**
	 * Computes the hashcode of an array using the elements in the given range.
	 * <p>
	 * This method computes the hashcode the same ways as {@link Arrays#hashCode(byte[])}, but works only on a range of
	 * the elements.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The staring offset index of the range. (inclusive)
	 * @param length
	 *            The number of elements in the range.
	 * @return The computed hashcode.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static int arrayRangeHashCode(byte array[], int offset, int length) throws IndexOutOfBoundsException {
		if (array == null) {
			return 0;
		}

		requireArrayRangeLengthImpl(array.length, offset, length);
		int result = 1;
		int end = offset + length;
		for (int i = offset; i < end; i++) {
			result = 31 * result + array[i];
		}
		return result;
	}

	/**
	 * Compares the elements in the argument array ranges for equality.
	 * <p>
	 * This method works the same way as {@link Arrays#equals(Object[], Object[])}, but compares subranges of the arrays
	 * instead of the entirety of them.
	 * <p>
	 * If any of the arrays are <code>null</code>, this method will return <code>true</code>, of the another array is
	 * <code>null</code> as well, else <code>false</code>.
	 * 
	 * @param first
	 *            The first array.
	 * @param firstoffset
	 *            The starting offset index of the range in the first array. (inclusive)
	 * @param second
	 *            The second array.
	 * @param secondoffset
	 *            The starting offset index of the range in the second array. (inclusive)
	 * @param length
	 *            The number of elements to compare.
	 * @return <code>true</code> if all elements in the specified arrays are equal.
	 * @throws IndexOutOfBoundsException
	 *             If a specified range is out of bounds.
	 */
	public static boolean arrayRangeEquals(Object[] first, int firstoffset, Object[] second, int secondoffset,
			int length) throws IndexOutOfBoundsException {
		if (first == null) {
			return second == null;
		}
		if (second == null) {
			return false;
		}
		requireArrayRangeLengthImpl(first.length, firstoffset, length);
		requireArrayRangeLengthImpl(second.length, secondoffset, length);
		for (int i = 0; i < length; i++) {
			if (!Objects.equals(first[firstoffset + i], second[secondoffset + i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Creates a new {@link List} that is backed by the argument array.
	 * <p>
	 * The array may have a primitive component type, it will be handled accordingly. The underlying implementation uses
	 * the {@link Array} class to access the elements.
	 * <p>
	 * The returned list is modifiable, and modifications will propagate back to the argument array.
	 * 
	 * @param array
	 *            The array to wrap into a list.
	 * @return The list, or <code>null</code> if the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 * @see ImmutableUtils#unmodifiableReflectionArrayList(Object)
	 */
	public static List<?> arrayReflectionList(Object array) throws IllegalArgumentException {
		//XXX specialize for primitive arrays?
		if (array == null) {
			return null;
		}
		//checks if the argument is actually an array
		int len = Array.getLength(array);
		if (len == 0) {
			return Collections.emptyList();
		}
		return new ReflectionArrayList(array, len);
	}

	/**
	 * Creates a new {@link List} that is backed by the specified range of the argument array.
	 * <p>
	 * The array may have a primitive component type, it will be handled accordingly. The underlying implementation uses
	 * the {@link Array} class to access the elements.
	 * <p>
	 * The returned list is modifiable, and modifications will propagate back to the argument array.
	 * 
	 * @param array
	 *            The array to wrap into a list.
	 * @param offset
	 *            The offset where the returned list should start.
	 * @param length
	 *            The number of elements in the range.
	 * @return The list, or <code>null</code> if the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static List<?> arrayReflectionList(Object array, int offset, int length)
			throws IllegalArgumentException, IndexOutOfBoundsException {
		//XXX specialize for primitive arrays?
		if (array == null) {
			return null;
		}
		if (length == 0) {
			return Collections.emptyList();
		}
		int arraylen = Array.getLength(array);
		requireArrayRangeLength(arraylen, offset, length);
		return new ReflectionOffsetArrayList(array, offset, length);
	}

	/**
	 * Finds the index of an object in the specified array.
	 * 
	 * @param array
	 *            The array to search in.
	 * @param object
	 *            The object to search for.
	 * @return The index of the object in the array or -1 if not found.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static int arrayIndexOf(Object[] array, Object object) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		return arrayIndexOf(array, 0, array.length, object);
	}

	/**
	 * Finds the index of an object in the specified array range.
	 * 
	 * @param array
	 *            The array to search in.
	 * @param start
	 *            The start index of the search range. (inclusive)
	 * @param end
	 *            The end index of the search range. (exclusive)
	 * @param object
	 *            The object to search for.
	 * @return The index of the object in the array or -1 if not found. (The index is relative to the start of the
	 *             array, not the start of the range.)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the range is exceeds the array boundaries. (May be only thrown if the elements outside the
	 *             boundaries are actually accessed.)
	 */
	public static int arrayIndexOf(Object[] array, int start, int end, Object object)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		if (object == null) {
			for (int i = start; i < end; i++) {
				if (array[i] == null) {
					return i;
				}
			}
		} else {
			for (int i = start; i < end; i++) {
				if (object.equals(array[i])) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Finds the last index of an object in the specified array.
	 * 
	 * @param array
	 *            The array to search in.
	 * @param object
	 *            The object to search for.
	 * @return The last index of the object in the array or -1 if not found.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static int arrayLastIndexOf(Object[] array, Object object) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		return arrayLastIndexOf(array, 0, array.length, object);
	}

	/**
	 * Finds the last index of an object in the specified array range.
	 * 
	 * @param array
	 *            The array to search in.
	 * @param start
	 *            The start index of the search range. (inclusive)
	 * @param end
	 *            The end index of the search range. (exclusive)
	 * @param object
	 *            The object to search for.
	 * @return The index of the object in the array or -1 if not found. (The index is relative to the start of the
	 *             array, not the start of the range.)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the range is exceeds the array boundaries. (May be only thrown if the elements outside the
	 *             boundaries are actually accessed.)
	 */
	public static int arrayLastIndexOf(Object[] array, int start, int end, Object object)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		if (object == null) {
			for (int i = end - 1; i >= start; i--) {
				if (array[i] == null) {
					return i;
				}
			}
		} else {
			for (int i = end - 1; i >= start; i--) {
				if (object.equals(array[i])) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Creates a new array by appending the given element at the end of it.
	 * <p>
	 * This method will create a new array that has the length <code>array.length + 1</code> and will set the element at
	 * <code>array.length</code> to the argument.
	 * <p>
	 * It is recommended not to call this function repeatedly (i.e. in a loop), as a new array is created each time it
	 * is called.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param array
	 *            The array to append the element to.
	 * @param element
	 *            The element to append.
	 * @return The new array with the additional element.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static <T> T[] appended(T[] array, T element) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		T[] copy = Arrays.copyOf(array, array.length + 1);
		copy[array.length] = element;
		return copy;
	}

	/**
	 * Creates a new array by prepending the given element at the start of it.
	 * <p>
	 * This method will create a new array that has the length <code>array.length + 1</code> and will set the element at
	 * <code>0</code> to the argument.
	 * <p>
	 * It is recommended not to call this function repeatedly (i.e. in a loop), as a new array is created each time it
	 * is called.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param array
	 *            The array to prepend the element to.
	 * @param element
	 *            The element to prepend.
	 * @return The new array with the additional element.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public static <T> T[] prepended(T[] array, T element) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		@SuppressWarnings("unchecked")
		T[] copy = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length + 1);
		copy[0] = element;
		System.arraycopy(array, 0, copy, 1, array.length);
		return copy;
	}

	/**
	 * Creates a new array by inserting the given element at the specified position.
	 * <p>
	 * This method will create a new array that has the length <code>array.length + 1</code> and will insert the element
	 * at the specified position.
	 * <p>
	 * It is recommended not to call this function repeatedly (i.e. in a loop), as a new array is created each time it
	 * is called.
	 * <p>
	 * If the insertion index equals to <code>array.length</code>, this method works the same way as
	 * {@link #appended(Object[], Object)}.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param array
	 *            The array to insert the element for.
	 * @param index
	 *            The index at which to insert the element.
	 * @param element
	 *            The element to insert.
	 * @return The new array with the inserted element.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the index is negative or greater than <code>array.length</code>.
	 */
	public static <T> T[] inserted(T[] array, int index, T element)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		if (index > array.length || index < 0) {
			throw new IndexOutOfBoundsException(index + " for length: " + array.length);
		}
		if (index == array.length) {
			T[] copy = Arrays.copyOf(array, array.length + 1);
			copy[array.length] = element;
			return copy;
		}
		if (index == 0) {
			return prepended(array, element);
		}
		@SuppressWarnings("unchecked")
		T[] copy = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length + 1);
		System.arraycopy(array, 0, copy, 0, index);
		copy[index] = element;
		System.arraycopy(array, index, copy, index + 1, array.length - index);
		return copy;
	}

	/**
	 * Removes the element at the specified index from the given array.
	 * <p>
	 * This method will create a new array that doesn't contain the element at the specified index.
	 * 
	 * @param <T>
	 *            The component type of the array.
	 * @param array
	 *            The array.
	 * @param index
	 *            The index of the element to remove.
	 * @return The new array that doesn't contain the specified element.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the index is out of bounds for the array.
	 */
	public static <T> T[] removedAtIndex(T[] array, int index) throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		if (index < 0 || index >= array.length) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		if (index == 0) {
			return Arrays.copyOfRange(array, 1, array.length);
		}
		if (index == array.length - 1) {
			return Arrays.copyOf(array, index);
		}
		@SuppressWarnings("unchecked")
		T[] result = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length - 1);
		System.arraycopy(array, 0, result, 0, index);
		System.arraycopy(array, index + 1, result, index, array.length - index - 1);
		return result;
	}

	/**
	 * Concatenates the specified arrays.
	 * <p>
	 * The method creates a new array that contains the elements from the first and the second in this order.
	 * <p>
	 * The component type of the resulting array is either the same as the first or the second array arguments.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param first
	 *            The first array.
	 * @param second
	 *            The second array.
	 * @return A new array that contains the elements from the specified arrays.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <T> T[] concat(T[] first, T[] second) throws NullPointerException {
		Objects.requireNonNull(first, "first array");
		Objects.requireNonNull(second, "second array");
		if (first.length == 0) {
			return second.clone();
		}
		if (second.length == 0) {
			return first.clone();
		}
		T[] result = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	/**
	 * Checks if the specified range of byte arrays equal to each other.
	 * <p>
	 * This method will use the vectorized comparison support on JDK implementations which support it. (Usually JDK9+)
	 * 
	 * @param first
	 *            The first array.
	 * @param firstoffset
	 *            The offset in the first array where the range starts.
	 * @param second
	 *            The second array.
	 * @param secondoffset
	 *            The offset in the second array where the range starts.
	 * @param length
	 *            The number of bytes to compare. The length of both ranges.
	 * @return <code>true</code> if the specified ranges contain the same bytes.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If any of the specified range is out of bounds.
	 */
	public static boolean regionEquals(byte[] first, int firstoffset, byte[] second, int secondoffset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		requireArrayRange(first, firstoffset, length);
		requireArrayRange(second, secondoffset, length);
		return ArrayAccessor.equals(first, firstoffset, second, secondoffset, length);
	}

	/**
	 * Finds the index of the first mismatching bytes in the specified byte array ranges.
	 * <p>
	 * The method will iterate over the bytes in the specified ranges, and will return the relative index of the first
	 * one that doesn't equal. If all bytes equal in the specified ranges, the method will return -1.
	 * <p>
	 * The returned relative index is to be interpreted against the specified starting offsets instead of the start of
	 * the arrays.
	 * <p>
	 * This method uses iterating implementation on JDK 8, and uses vectorized implementation on JDK 9+.
	 * 
	 * @param first
	 *            The first array of bytes.
	 * @param firstoffset
	 *            The starting offset of the range in the first array.
	 * @param second
	 *            The second array of bytes.
	 * @param secondoffset
	 *            The starting offset of the range in the second array.
	 * @param length
	 *            The number of bytes to compare.
	 * @return The relative index of the first mismatching byte or -1 if the ranges equal.
	 * @throws NullPointerException
	 *             If any of the arrays are <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If any of the specified range is out of bounds.
	 */
	public static int mismatch(byte[] first, int firstoffset, byte[] second, int secondoffset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		requireArrayRange(first, firstoffset, length);
		requireArrayRange(second, secondoffset, length);
		return ArrayAccessor.mismatch(first, firstoffset, second, secondoffset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(byte[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(short[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(int[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(long[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(float[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(double[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(char[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(boolean[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRange(Object[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayRangeLengthImpl(array.length, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array given by its length.
	 * 
	 * @param arraylength
	 *            The length of the associated array.
	 * @param offset
	 *            The starting offset index of the range. (inclusive)
	 * @param length
	 *            The length of the range.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayRangeLength(int arraylength, int offset, int length)
			throws IndexOutOfBoundsException {
		requireArrayRangeLengthImpl(arraylength, offset, length);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(byte[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(short[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(int[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(long[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(float[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(double[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(char[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(boolean[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array.
	 * 
	 * @param array
	 *            The array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRange(Object[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(array, "array");
		requireArrayStartEndRangeImpl(array.length, start, end);
	}

	/**
	 * Validation method to check if a specified range lies within the argument array given by its length.
	 * 
	 * @param arraylength
	 *            The length of the associated array.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void requireArrayStartEndRangeLength(int arraylength, int start, int end)
			throws IndexOutOfBoundsException {
		requireArrayStartEndRangeImpl(arraylength, start, end);
	}

	private static void requireArrayStartEndRangeImpl(int arraylength, int start, int end)
			throws IndexOutOfBoundsException {
		//we don't need to check if end < 0, as we check that it is greater than or equals to start, and start is always non-negative
		if (start < 0 || start > end || end > arraylength) {
			throw new IndexOutOfBoundsException("Invalid bounds [" + start + ", " + end + ") for size: " + arraylength);
		}
	}

	private static void requireArrayRangeLengthImpl(int arraylength, int offset, int length) {
		if (offset < 0 || length < 0 || offset + length > arraylength) {
			throw new ArrayIndexOutOfBoundsException(
					"Offset: " + offset + " length: " + length + " is out of range for size: " + arraylength);
		}
	}

	private static void toStringReflectionNonEmptyArrayImpl(Object array, int len, StringBuilder sb) {
		sb.append('[');
		int i = 0;
		while (true) {
			sb.append(Array.get(array, i));
			if (++i < len) {
				sb.append(", ");
			} else {
				break;
			}
		}
		sb.append(']');
	}

	private ArrayUtils() {
		throw new UnsupportedOperationException();
	}
}
