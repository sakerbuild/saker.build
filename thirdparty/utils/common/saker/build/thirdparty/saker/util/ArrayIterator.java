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

import java.util.ConcurrentModificationException;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link ListIterator} implementation backed by an array.
 * <p>
 * The iterator is unmodifiable.
 * <p>
 * The modification of the backing array will not cause this iterator to throw {@link ConcurrentModificationException}.
 * 
 * @param <T>
 *            The element type.
 */
public class ArrayIterator<T> implements ListIterator<T> {
	private T[] values;
	private int start;
	private int end;
	private int index;

	/**
	 * Creates a new iterator with the parameter array. The iterator range is the full array.
	 * 
	 * @param values
	 *            The backing array.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public ArrayIterator(T[] values) throws NullPointerException {
		Objects.requireNonNull(values, "values");
		this.values = values;
		this.end = values.length;
	}

	/**
	 * Creates a new iterator with the parameter array and specified range.
	 * 
	 * @param values
	 *            The backing array.
	 * @param start
	 *            The range start index (inclusive).
	 * @param end
	 *            The range end index (exclusive).
	 * @throws IndexOutOfBoundsException
	 *             If the range is not in the argument array.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public ArrayIterator(T[] values, int start, int end) throws IndexOutOfBoundsException, NullPointerException {
		ArrayUtils.requireArrayStartEndRange(values, start, end);
		this.values = values;
		this.start = start;
		this.index = start;
		this.end = end;
	}

	/**
	 * Creates a new iterator with the parameter array, specified range, and initial index. The first call to
	 * {@link #next()} will return the element at the specified index.
	 * 
	 * @param values
	 *            The backing array.
	 * @param start
	 *            The range start index (inclusive).
	 * @param end
	 *            The range end index (exclusive).
	 * @param index
	 *            The initial index.
	 * @throws IndexOutOfBoundsException
	 *             If the range or index is not in the argument array.
	 * @throws NullPointerException
	 *             If the array is <code>null</code>.
	 */
	public ArrayIterator(T[] values, int start, int end, int index)
			throws IndexOutOfBoundsException, NullPointerException {
		ArrayUtils.requireArrayStartEndRange(values, start, end);
		if (index < start || index > end) {
			throw new IndexOutOfBoundsException("Invalid bounds [" + start + ", " + end + ") for length: "
					+ values.length + " and starting index: " + index);
		}
		this.values = values;
		this.start = start;
		this.end = end;
		this.index = index;
	}

	@Override
	public boolean hasNext() {
		return index < end;
	}

	@Override
	public T next() {
		if (index >= end) {
			throw new NoSuchElementException("Iterator is out of range.");
		}
		return values[index++];
	}

	@Override
	public boolean hasPrevious() {
		return index > start;
	}

	@Override
	public T previous() {
		if (index < start) {
			throw new NoSuchElementException("Iterator is out of range.");
		}
		return values[--index];
	}

	@Override
	public int nextIndex() {
		return index + 1;
	}

	@Override
	public int previousIndex() {
		return index - 1;
	}

	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		Objects.requireNonNull(action, "action");
		while (index < end) {
			action.accept(values[index++]);
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(T e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException();
	}

}
