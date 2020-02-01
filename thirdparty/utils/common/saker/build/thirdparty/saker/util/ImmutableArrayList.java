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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

class ImmutableArrayList<E> implements List<E>, RandomAccess, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Object[] items;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableArrayList() {
	}

	private ImmutableArrayList(Object[] items) {
		this.items = items;
	}

	public static <E> List<E> create(E[] items) {
		if (ObjectUtils.isNullOrEmpty(items)) {
			return Collections.emptyList();
		}
		return new ImmutableArrayList<>(items);
	}

	@SuppressWarnings("unchecked")
	@Override
	public E get(int index) {
		return (E) items[index];
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ListIterator<E> listIterator() {
		return new ArrayIterator(items);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ListIterator<E> listIterator(int index) {
		return new ArrayIterator(items, 0, items.length, index);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
			throw new IndexOutOfBoundsException(fromIndex + " - " + toIndex);
		}
		return ImmutableRangeArrayList.create(items, fromIndex, toIndex);
	}

	@Override
	public Iterator<E> iterator() {
		return listIterator();
	}

	@Override
	public int size() {
		return items.length;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void forEach(Consumer<? super E> action) {
		for (int i = 0; i < items.length; i++) {
			action.accept((E) items[i]);
		}
	}

	@Override
	public int lastIndexOf(Object o) {
		return ArrayUtils.arrayLastIndexOf(items, o);
	}

	@Override
	public int indexOf(Object o) {
		return ArrayUtils.arrayIndexOf(items, o);
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
	public boolean isEmpty() {
		//instance of this is never created for empty array
		return false;
	}

	@Override
	public boolean contains(Object object) {
		if (object == null) {
			for (Object o : items) {
				if (o == null) {
					return true;
				}
			}
		} else {
			for (Object o : items) {
				if (object.equals(o)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public Object[] toArray() {
		return items.clone();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		if (a.length < items.length) {
			a = Arrays.copyOf(a, items.length);
			System.arraycopy(items, 0, a, 0, items.length);
			//no need to check for appending null
			return a;
		}
		System.arraycopy(items, 0, a, 0, items.length);
		if (a.length > items.length) {
			a[items.length] = null;
		}
		return a;
	}

	@Override
	public boolean add(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c) {
			if (!contains(o)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void replaceAll(UnaryOperator<E> operator) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sort(Comparator<? super E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof List)) {
			return false;
		}
		return ObjectUtils.listsEqual(this, (List<?>) o);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.listHash(this);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(items);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		items = (Object[]) in.readObject();
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}

}
