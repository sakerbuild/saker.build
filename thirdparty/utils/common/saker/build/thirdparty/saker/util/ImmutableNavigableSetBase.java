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

import java.util.AbstractSet;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedSet;

abstract class ImmutableNavigableSetBase<E> extends AbstractSet<E> implements NavigableSet<E> {
	protected abstract E elementAt(int idx);

	protected abstract int binarySearch(E e);

	protected final int floorIndex(E e) {
		int idx = binarySearch(e);
		if (idx >= 0) {
			return idx;
		}
		int insertionidx = -idx - 1;
		if (insertionidx == 0) {
			return -1;
		}
		return insertionidx - 1;
	}

	protected final int ceilingIndex(E e) {
		int idx = binarySearch(e);
		if (idx >= 0) {
			return idx;
		}
		int insertionidx = -idx - 1;
		if (insertionidx == size()) {
			return -1;
		}
		return insertionidx;
	}

	protected final int higherIndex(E e) {
		int idx = binarySearch(e);
		if (idx >= 0) {
			if (idx + 1 == size()) {
				return -1;
			}
			return idx + 1;
		}
		int insertionidx = -idx - 1;
		if (insertionidx == size()) {
			return -1;
		}
		return insertionidx;
	}

	protected final int lowerIndex(E e) {
		int idx = binarySearch(e);
		if (idx > 0) {
			return idx - 1;
		}
		if (idx == 0) {
			return -1;
		}
		int insertionidx = -idx - 1;
		if (insertionidx == 0) {
			return -1;
		}
		return insertionidx - 1;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean contains(Object o) {
		return binarySearch((E) o) >= 0;
	}

	@Override
	public final E lower(E e) {
		int idx = lowerIndex(e);
		return idx < 0 ? null : elementAt(idx);
	}

	@Override
	public final E floor(E e) {
		int idx = floorIndex(e);
		return idx < 0 ? null : elementAt(idx);
	}

	@Override
	public final E ceiling(E e) {
		int idx = ceilingIndex(e);
		return idx < 0 ? null : elementAt(idx);
	}

	@Override
	public final E higher(E e) {
		int idx = higherIndex(e);
		return idx < 0 ? null : elementAt(idx);
	}

	@Override
	public final E first() {
		if (isEmpty()) {
			throw new NoSuchElementException();
		}
		return elementAt(0);
	}

	@Override
	public final E last() {
		if (isEmpty()) {
			throw new NoSuchElementException();
		}
		return elementAt(size() - 1);
	}

	@Override
	public final SortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	public final SortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	public final SortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	@Override
	public final E pollFirst() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final E pollLast() {
		throw new UnsupportedOperationException();
	}
}
