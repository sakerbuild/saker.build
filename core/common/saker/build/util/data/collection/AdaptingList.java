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
package saker.build.util.data.collection;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.DataConverterUtils;

public class AdaptingList extends AdaptingCollection<List<?>> implements List<Object> {
	private final class ListIteratorImplementation implements ListIterator<Object> {
		private ListIterator<?> it;

		public ListIteratorImplementation(ListIterator<?> it) {
			this.it = it;
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public Object next() {
			return DataConverterUtils.adaptInterface(cl, it.next());
		}

		@Override
		public boolean hasPrevious() {
			return it.hasPrevious();
		}

		@Override
		public Object previous() {
			return DataConverterUtils.adaptInterface(cl, it.previous());
		}

		@Override
		public int nextIndex() {
			return it.nextIndex();
		}

		@Override
		public int previousIndex() {
			return it.previousIndex();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void set(Object e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(Object e) {
			throw new UnsupportedOperationException();
		}
	}

	public AdaptingList(ClassLoader cl, List<?> iterable) {
		super(cl, iterable);
	}

	@Override
	public Object get(int index) {
		return DataConverterUtils.adaptInterface(cl, coll.get(index));
	}

	@Override
	public int indexOf(Object o) {
		// Object
		int size = size();
		for (int i = 0; i < size; i++) {
			if (Objects.equals(o, get(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		// Object
		int size = size();
		for (int i = size - 1; i >= 0; i--) {
			if (Objects.equals(o, get(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public ListIterator<Object> listIterator() {
		return new ListIteratorImplementation(coll.listIterator());
	}

	@Override
	public ListIterator<Object> listIterator(int index) {
		return new ListIteratorImplementation(coll.listIterator(index));
	}

	@Override
	public List<Object> subList(int fromIndex, int toIndex) {
		return new AdaptingList(cl, coll.subList(fromIndex, toIndex));
	}

	@Override
	public Object set(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		return ObjectUtils.listHash(this);
	}

	@Override
	public boolean equals(Object o) {
		// based on AbstractList source code
		if (o == this)
			return true;
		if (!(o instanceof List))
			return false;
		ListIterator<?> e1 = listIterator();
		ListIterator<?> e2 = ((List<?>) o).listIterator();
		while (e1.hasNext() && e2.hasNext()) {
			Object o1 = e1.next();
			Object o2 = e2.next();
			if (!Objects.equals(o1, o2)) {
				return false;
			}
		}
		return !(e1.hasNext() || e2.hasNext());
	}

}
