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
class ImmutableArrayNavigableSet<E> extends ImmutableNavigableSetBase<E> implements Externalizable {
	private static final long serialVersionUID = 1L;

	private Object[] items;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableArrayNavigableSet() {
	}

	protected ImmutableArrayNavigableSet(Object[] items) {
		this.items = items;
	}

	//doc: responsibility of the caller to provide valid parameters
	public static <E> NavigableSet<E> create(Comparator<? super E> comparator, Object[] items) {
		if (items.length == 0) {
			return ImmutableUtils.emptyNavigableSet(comparator);
		}
		if (comparator == null) {
			return new ImmutableArrayNavigableSet<>(items);
		}
		return new ImmutableArrayComparatorNavigableSet<>(items, comparator);
	}

	public static <E> NavigableSet<E> create(Comparator<? super E> comparator, Object[] items, int start, int end) {
		//range validity is satisfied by caller
		if (start == end) {
			return ImmutableUtils.emptyNavigableSet(comparator);
		}
		if (start == 0 && end == items.length) {
			if (comparator == null) {
				return new ImmutableArrayNavigableSet<>(items);
			}
			return new ImmutableArrayComparatorNavigableSet<>(items, comparator);
		}
		if (comparator == null) {
			return new ImmutableRangeArrayNavigableSet<>(items, start, end);
		}
		return new ImmutableRangeArrayComparatorNavigableSet<>(items, start, end, comparator);
	}

	@Override
	public Object[] toArray() {
		return items.clone();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		if (a.length >= items.length) {
			System.arraycopy(items, 0, a, 0, items.length);
			if (a.length > items.length) {
				a[items.length] = null;
			}
			return a;
		}
		Object result = Array.newInstance(a.getClass().getComponentType(), items.length);
		System.arraycopy(items, 0, result, 0, items.length);
		return (T[]) result;
	}

	@Override
	public Comparator<? super E> comparator() {
		return null;
	}

	@Override
	protected final E elementAt(int idx) {
		return (E) items[idx];
	}

	@Override
	protected final int binarySearch(E e) {
		return Arrays.binarySearch((E[]) items, e, comparator());
	}

	@Override
	public int size() {
		return items.length;
	}

	@Override
	public boolean isEmpty() {
		//the set cannot be constructed for empty list
		return false;
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return new ReverseNavigableSetView<>(this);
	}

	@Override
	public Iterator<E> descendingIterator() {
		//XXX implement a direct reverse iterator?
		return new ImmutableReverseIterator<>((E[]) items);
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
		return ImmutableArrayNavigableSet.create(comparator(), items, fromidx, toidx + 1);
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		int idx = inclusive ? floorIndex(toElement) : lowerIndex(toElement);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return ImmutableArrayNavigableSet.create(comparator(), items, 0, idx + 1);
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		int idx = inclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return ImmutableArrayNavigableSet.create(comparator(), items, idx, size());
	}

	@Override
	public final ListIterator<E> iterator() {
		//safe cast
		return new ArrayIterator<>((E[]) items);
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		for (Object i : items) {
			action.accept((E) i);
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
