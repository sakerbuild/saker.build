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

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;

import saker.build.thirdparty.saker.util.ImmutableUtils;

@SuppressWarnings("rawtypes")
public class SingleItemWrapper extends AbstractCollection implements List, Set, Deque, Cloneable {
	protected final Object item;
	protected final Function<Iterable<?>, Integer> hashCodeComputer;

	public SingleItemWrapper(Object item, Function<Iterable<?>, Integer> hashCodeComputer) {
		this.item = item;
		this.hashCodeComputer = hashCodeComputer;
	}

	@Override
	public SingleItemWrapper clone() {
		try {
			return (SingleItemWrapper) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean contains(Object o) {
		return Objects.equals(item, o);
	}

	@Override
	public Iterator iterator() {
		return ImmutableUtils.singletonIterator(item);
	}

	@Override
	public Object[] toArray() {
		return new Object[] { item };
	}

	@Override
	public boolean add(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection c) {
		for (Object o : c) {
			if (!contains(o)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object get(int index) {
		if (index != 0) {
			throw new IndexOutOfBoundsException(Integer.toString(index));
		}
		return item;
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
	public Object remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		return contains(o) ? 0 : -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		return indexOf(o);
	}

	@Override
	public ListIterator listIterator() {
		return listIterator(0);
	}

	@Override
	public ListIterator listIterator(int index) {
		return new ListIterator() {
			private int idx = index;

			@Override
			public boolean hasNext() {
				return idx == 0;
			}

			@Override
			public Object next() {
				++idx;
				return item;
			}

			@Override
			public boolean hasPrevious() {
				return idx == 1;
			}

			@Override
			public Object previous() {
				--idx;
				return item;
			}

			@Override
			public int nextIndex() {
				return idx + 1;
			}

			@Override
			public int previousIndex() {
				return idx - 1;
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
		};
	}

	@Override
	public List subList(int fromIndex, int toIndex) {
		if (fromIndex == 0) {
			if (toIndex == 1) {
				return this;
			}
			if (toIndex == 0) {
				return Collections.emptyList();
			}
			throw new IndexOutOfBoundsException(fromIndex + " - " + toIndex);
		}
		if (fromIndex != 1 && toIndex != 1) {
			throw new IndexOutOfBoundsException(fromIndex + " - " + toIndex);
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "[" + item.toString() + "]";
	}

	@Override
	public int hashCode() {
		return hashCodeComputer.apply(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof Collection) {
			Collection o = (Collection) obj;
			if (o.isEmpty()) {
				return false;
			}
			Iterator it = o.iterator();
			Object oobj = it.next();
			if (it.hasNext()) {
				// it contains more than one
				return false;
			}
			return Objects.equals(item, oobj);
		}
		return false;
	}

	@Override
	public boolean offer(Object e) {
		return false;
	}

	@Override
	public Object remove() {
		// E
		throw new UnsupportedOperationException();
	}

	@Override
	public Object poll() {
		// E
		throw new UnsupportedOperationException();
	}

	@Override
	public Object element() {
		// E
		return item;
	}

	@Override
	public Object peek() {
		// E
		return item;
	}

	@Override
	public void addFirst(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addLast(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean offerFirst(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean offerLast(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object removeFirst() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object removeLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object pollFirst() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object pollLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object getFirst() {
		return item;
	}

	@Override
	public Object getLast() {
		return item;
	}

	@Override
	public Object peekFirst() {
		return item;
	}

	@Override
	public Object peekLast() {
		return item;
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void push(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object pop() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator descendingIterator() {
		return iterator();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Spliterator spliterator() {
		return Spliterators.spliterator(this,
				Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.SIZED);
	}
}
