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
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.function.Consumer;

class FakeSortedSet<E> extends AbstractSet<E> implements SortedSet<E> {
	private final Iterable<? extends E> sorted;
	private final int size;
	private final Comparator<? super E> comparator;

	public FakeSortedSet(Iterable<? extends E> sorted, int size, Comparator<? super E> comparator) {
		this.sorted = sorted;
		this.size = size;
		this.comparator = comparator;
	}

	public FakeSortedSet(Collection<? extends E> sorted, Comparator<? super E> comparator) {
		this(sorted, sorted.size(), comparator);
	}

	public FakeSortedSet(Collection<? extends E> sorted) {
		this(sorted, null);
	}

	public FakeSortedSet(Iterable<? extends E> sorted, int size) {
		this(sorted, size, null);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterator<E> iterator() {
		//unchecked cast, but is actually fine.
		return (Iterator<E>) sorted.iterator();
	}

	@Override
	public Comparator<? super E> comparator() {
		return comparator;
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		sorted.forEach(action);
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E first() {
		throw new UnsupportedOperationException();
	}

	@Override
	public E last() {
		throw new UnsupportedOperationException();
	}

}
