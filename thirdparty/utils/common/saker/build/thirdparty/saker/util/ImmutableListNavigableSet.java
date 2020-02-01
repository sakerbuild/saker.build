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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.function.Consumer;

class ImmutableListNavigableSet<E> extends ImmutableNavigableSetBase<E> implements Externalizable {
	private static final long serialVersionUID = 1L;

	private List<? extends E> items;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableListNavigableSet() {
	}

	protected ImmutableListNavigableSet(List<? extends E> items) {
		this.items = items;
	}

	//doc: responsibility of the caller to provide valid parameters
	public static <E> NavigableSet<E> create(Comparator<? super E> comparator, List<? extends E> items) {
		if (items.isEmpty()) {
			return ImmutableUtils.emptyNavigableSet(comparator);
		}
		if (comparator == null) {
			return new ImmutableListNavigableSet<>(items);
		}
		return new ImmutableListComparatorNavigableSet<>(items, comparator);
	}

	@Override
	public Object[] toArray() {
		return items.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return items.toArray(a);
	}

	@Override
	public Comparator<? super E> comparator() {
		return null;
	}

	@Override
	protected final E elementAt(int idx) {
		return items.get(idx);
	}

	@Override
	protected final int binarySearch(E e) {
		return Collections.binarySearch(items, e, comparator());
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.isEmpty();
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return create(Collections.reverseOrder(comparator()), ObjectUtils.reversedList(items));
	}

	@Override
	public Iterator<E> descendingIterator() {
		return new ImmutableReverseIterator<>(items);
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
		return create(comparator(), items.subList(fromidx, toidx + 1));
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		int idx = inclusive ? floorIndex(toElement) : lowerIndex(toElement);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return create(comparator(), items.subList(0, idx + 1));
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		int idx = inclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableSet(comparator());
		}
		return create(comparator(), items.subList(idx, size()));
	}

	@SuppressWarnings({ "unchecked" })
	@Override
	public Iterator<E> iterator() {
		//safe cast
		return (Iterator<E>) items.iterator();
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		items.forEach(action);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(items);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		items = (List<E>) in.readObject();
	}

}
