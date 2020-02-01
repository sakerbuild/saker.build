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
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

class ImmutableReflectionOffsetArrayListIterator<T> implements ListIterator<T> {

	protected final Object array;
	protected final int offset;
	protected final int length;

	protected int index;

	public ImmutableReflectionOffsetArrayListIterator(Object array, int offset, int length) {
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	public ImmutableReflectionOffsetArrayListIterator(Object array, int offset, int length, int index) {
		this.array = array;
		this.offset = offset;
		this.length = length;
		this.index = index;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		Objects.requireNonNull(action, "action");
		while (index < length) {
			action.accept((T) Array.get(array, this.offset + index++));
		}
	}

	@Override
	public boolean hasNext() {
		return index < length;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		return (T) Array.get(array, this.offset + index++);
	}

	@Override
	public boolean hasPrevious() {
		return index > 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T previous() {
		if (!hasPrevious()) {
			throw new NoSuchElementException();
		}
		return (T) Array.get(array, this.offset + --index);
	}

	@Override
	public int nextIndex() {
		return index;
	}

	@Override
	public int previousIndex() {
		return index - 1;
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
