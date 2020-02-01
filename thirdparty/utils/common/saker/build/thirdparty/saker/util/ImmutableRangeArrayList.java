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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

class ImmutableRangeArrayList<E> extends AbstractCollection<E> implements List<E>, RandomAccess, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Object[] items;
	protected int start;
	protected int end;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableRangeArrayList() {
	}

	private ImmutableRangeArrayList(Object[] items, int start, int end) {
		this.items = items;
		this.start = start;
		this.end = end;
	}

	@SuppressWarnings("unchecked")
	public static <E> List<E> create(Object[] items, int start, int end) {
		if (ObjectUtils.isNullOrEmpty(items)) {
			return Collections.emptyList();
		}
		ArrayUtils.requireArrayStartEndRange(items, start, end);
		if (start == 0 && end == items.length) {
			return (List<E>) ImmutableArrayList.create(items);
		}
		return new ImmutableRangeArrayList<>(items, start, end);
	}

	@Override
	public Iterator<E> iterator() {
		return listIterator();
	}

	@Override
	public int size() {
		return end - start;
	}

	@SuppressWarnings("unchecked")
	@Override
	public E get(int index) {
		if (index < 0 || index >= size()) {
			throw new IndexOutOfBoundsException(Integer.toBinaryString(index));
		}
		return (E) items[start + index];
	}

	@Override
	public int indexOf(Object o) {
		return ArrayUtils.arrayIndexOf(items, start, end, o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return ArrayUtils.arrayLastIndexOf(items, start, end, o);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public ListIterator<E> listIterator() {
		return new ArrayIterator(items, start, end);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public ListIterator<E> listIterator(int index) {
		if (index < 0 || index > size()) {
			throw new IndexOutOfBoundsException(index + " for size: " + size());
		}
		return new ArrayIterator(items, start, end, start + index);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		ArrayUtils.requireArrayStartEndRangeLength(size(), fromIndex, toIndex);
		return create(items, start + fromIndex, start + toIndex);
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E set(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(items);
		out.writeInt(start);
		out.writeInt(end);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		items = (Object[]) in.readObject();
		start = in.readInt();
		end = in.readInt();
	}

	@Override
	public int hashCode() {
		return ObjectUtils.listHash(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof List)) {
			return false;
		}
		return ObjectUtils.listsEqual(this, (List<?>) obj);
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}

}
