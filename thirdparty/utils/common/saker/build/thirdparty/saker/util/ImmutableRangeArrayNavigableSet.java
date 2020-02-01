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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NavigableSet;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
class ImmutableRangeArrayNavigableSet<E> extends ImmutableNavigableSetBase<E> implements Externalizable {
	private static final long serialVersionUID = 1L;

	private Object[] items;
	private int start;
	private int end;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableRangeArrayNavigableSet() {
	}

	protected ImmutableRangeArrayNavigableSet(Object[] items, int start, int end) {
		this.items = items;
		this.start = start;
		this.end = end;
	}

	@Override
	public Object[] toArray() {
		return Arrays.copyOfRange(items, start, end);
	}

	@Override
	public <T> T[] toArray(T[] a) {
		int size = this.size();
		if (a.length >= size) {
			System.arraycopy(items, start, a, 0, size);
			if (a.length > size) {
				a[size] = null;
			}
			return a;
		}
		Object result = Array.newInstance(a.getClass().getComponentType(), size);
		System.arraycopy(items, start, result, 0, size);
		return (T[]) result;
	}

	@Override
	public Comparator<? super E> comparator() {
		return null;
	}

	@Override
	protected final E elementAt(int idx) {
		if (idx < 0 || idx >= size()) {
			throw new IndexOutOfBoundsException(Integer.toString(idx));
		}
		return (E) items[start + idx];
	}

	@Override
	protected final int binarySearch(E e) {
		int res = Arrays.binarySearch((E[]) items, start, end, e, comparator());
		if (res < 0) {
			//not found
			return -((-res - 1) - start) - 1;
		} else {
			return res - start;
		}
	}

	@Override
	public int size() {
		return end - start;
	}

	@Override
	public boolean isEmpty() {
		//the set cannot be constructed for empty list
		return false;
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return ImmutableListNavigableSet.create(Collections.reverseOrder(comparator()),
				(List<? extends E>) ObjectUtils.reversedList(ImmutableUtils.unmodifiableArrayList(items, start, end)));
	}

	@Override
	public Iterator<E> descendingIterator() {
		//XXX implement a direct reverse iterator?
		return new ImmutableReverseIterator<>(iterator());
	}

	@Override
	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		int fromidx = fromInclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
		if (fromidx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		int toidx = toInclusive ? floorIndex(toElement) : lowerIndex(toElement);
		if (toidx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		if (toidx < fromidx) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return ImmutableArrayNavigableSet.create(comparator(), items, start + fromidx, start + toidx + 1);
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		int idx = inclusive ? floorIndex(toElement) : lowerIndex(toElement);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return ImmutableArrayNavigableSet.create(comparator(), items, start, start + idx + 1);
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		int idx = inclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return ImmutableArrayNavigableSet.create(comparator(), items, start + idx, end);
	}

	@Override
	public ListIterator<E> iterator() {
		//safe cast
		return new ArrayIterator<>((E[]) items, start, end);
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		for (int i = start; i < end; i++) {
			action.accept((E) items[i]);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(items);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		items = (Object[]) in.readObject();
	}

}
