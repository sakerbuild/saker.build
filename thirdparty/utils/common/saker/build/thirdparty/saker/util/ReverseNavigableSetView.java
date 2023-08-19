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
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.function.Predicate;

class ReverseNavigableSetView<E> extends AbstractSet<E> implements NavigableSet<E>, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<E> set;

	/**
	 * For {@link Externalizable}.
	 */
	public ReverseNavigableSetView() {
	}

	public ReverseNavigableSetView(NavigableSet<E> set) {
		this.set = set;
	}

	@Override
	public Comparator<? super E> comparator() {
		return Collections.reverseOrder(set.comparator());
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return set;
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public Iterator<E> iterator() {
		return set.descendingIterator();
	}

	@Override
	public Iterator<E> descendingIterator() {
		return set.iterator();
	}

	@Override
	public boolean add(E e) {
		return set.add(e);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return set.addAll(c);
	}

	@Override
	public void clear() {
		set.clear();
	}

	@Override
	public boolean contains(Object o) {
		return set.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return set.containsAll(c);
	}

	@Override
	public E first() {
		return set.last();
	}

	@Override
	public E last() {
		return set.first();
	}

	@Override
	public E lower(E e) {
		return set.higher(e);
	}

	@Override
	public E floor(E e) {
		return set.ceiling(e);
	}

	@Override
	public E ceiling(E e) {
		return set.floor(e);
	}

	@Override
	public E higher(E e) {
		return set.lower(e);
	}

	@Override
	public E pollFirst() {
		return set.pollLast();
	}

	@Override
	public E pollLast() {
		return set.pollFirst();
	}

	@Override
	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return set.subSet(toElement, toInclusive, fromElement, fromInclusive).descendingSet();
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return set.tailSet(toElement, inclusive).descendingSet();
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return set.headSet(fromElement, inclusive).descendingSet();
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		return set.subSet(toElement, false, fromElement, true).descendingSet();
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		return set.tailSet(toElement, false).descendingSet();
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		return set.headSet(fromElement, true).descendingSet();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return set.removeAll(c);
	}

	@Override
	public boolean remove(Object o) {
		return set.remove(o);
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		return set.removeIf(filter);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return set.retainAll(c);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(set);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		set = (NavigableSet<E>) in.readObject();
	}

}
