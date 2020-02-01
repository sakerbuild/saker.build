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
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@SuppressWarnings("rawtypes")
class ReflectionArrayList extends AbstractCollection implements List, RandomAccess, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Object array;
	protected transient int arrayLength;

	/**
	 * For {@link Externalizable}.
	 */
	public ReflectionArrayList() {
	}

	public ReflectionArrayList(Object array) {
		this.array = array;
		this.arrayLength = Array.getLength(array);
	}

	public ReflectionArrayList(Object array, int length) {
		this.array = array;
		this.arrayLength = length;
	}

	@Override
	public int size() {
		return arrayLength;
	}

	@Override
	public boolean isEmpty() {
		return arrayLength == 0;
	}

	@Override
	public Object[] toArray() {
		Object[] result = new Object[arrayLength];
		for (int i = 0; i < arrayLength; i++) {
			result[i] = Array.get(array, i);
		}
		return result;
	}

	@Override
	public Object get(int index) {
		return Array.get(array, index);
	}

	@Override
	public Object set(int index, Object element) {
		Object result = get(index);
		Array.set(array, index, element);
		return result;
	}

	@Override
	public Iterator iterator() {
		return listIterator();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void forEach(Consumer action) {
		for (int i = 0; i < arrayLength; i++) {
			action.accept(Array.get(array, i));
		}
	}

	@Override
	public int indexOf(Object o) {
		if (o == null) {
			for (int i = 0; i < arrayLength; i++) {
				if (Array.get(array, i) == null) {
					return i;
				}
			}
			return -1;
		}
		for (int i = 0; i < arrayLength; i++) {
			if (o.equals(Array.get(array, i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		if (o == null) {
			for (int i = arrayLength - 1; i >= 0; i--) {
				if (Array.get(array, i) == null) {
					return i;
				}
			}
			return -1;
		}
		for (int i = arrayLength - 1; i >= 0; i--) {
			if (o.equals(Array.get(array, i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public ListIterator listIterator() {
		return listIterator(0);
	}

	@Override
	public ListIterator listIterator(int index) {
		if (index < 0 || index > arrayLength) {
			throw new IndexOutOfBoundsException("index out of range. (" + index + " for length: " + arrayLength + ")");
		}
		return new ReflectionArrayListIterator<>(array, index);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(array);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		array = in.readObject();
		arrayLength = Array.getLength(array);
	}

	@Override
	public List subList(int fromIndex, int toIndex) {
		return ArrayUtils.arrayReflectionList(array, fromIndex, toIndex);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void replaceAll(UnaryOperator operator) {
		Objects.requireNonNull(operator, "operator");
		for (int i = 0; i < arrayLength; i++) {
			Object e = Array.get(array, i);
			Array.set(array, i, operator.apply(e));
		}
	}

	@Override
	public int hashCode() {
		int hashCode = 1;
		for (int i = 0; i < arrayLength; i++) {
			Object e = Array.get(array, i);
			hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof List)) {
			return false;
		}
		ListIterator<?> oit = ((List<?>) o).listIterator();
		int i = 0;
		while (oit.hasNext()) {
			if (i >= arrayLength) {
				//other list has more elements
				return false;
			}
			Object oe = oit.next();
			Object e = Array.get(array, i);
			if (!Objects.equals(oe, e)) {
				//element at index i differs
				return false;
			}
			++i;
		}
		if (i < arrayLength) {
			//we have more elements
			return false;
		}
		//same length, same elements
		return true;
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean add(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection c) {
		throw new UnsupportedOperationException();
	}

}
