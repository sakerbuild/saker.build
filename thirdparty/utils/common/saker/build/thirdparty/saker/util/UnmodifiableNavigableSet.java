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
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

class UnmodifiableNavigableSet<E> implements NavigableSet<E>, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<E> set;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableNavigableSet() {
	}

	public UnmodifiableNavigableSet(NavigableSet<E> set) {
		this.set = set;
	}

	@Override
	public Comparator<? super E> comparator() {
		return set.comparator();
	}

	@Override
	public E first() {
		return set.first();
	}

	@Override
	public E last() {
		return set.last();
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return set.contains(o);
	}

	@Override
	public Object[] toArray() {
		return set.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return set.toArray(a);
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
		return set.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public E lower(E e) {
		return set.lower(e);
	}

	@Override
	public E floor(E e) {
		return set.floor(e);
	}

	@Override
	public E ceiling(E e) {
		return set.ceiling(e);
	}

	@Override
	public E higher(E e) {
		return set.higher(e);
	}

	@Override
	public E pollFirst() {
		throw new UnsupportedOperationException();
	}

	@Override
	public E pollLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<E> iterator() {
		return new UnmodifiableIterator<>(set.iterator());
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return new UnmodifiableNavigableSet<>(set.descendingSet());
	}

	@Override
	public Iterator<E> descendingIterator() {
		return new UnmodifiableIterator<>(set.descendingIterator());
	}

	@Override
	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return new UnmodifiableNavigableSet<>(set.subSet(fromElement, fromInclusive, toElement, toInclusive));
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return new UnmodifiableNavigableSet<>(set.headSet(toElement, inclusive));
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return new UnmodifiableNavigableSet<>(set.tailSet(fromElement, inclusive));
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		set.forEach(action);
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

	@Override
	public int hashCode() {
		return set.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return set.equals(obj);
	}

	@Override
	public String toString() {
		return set.toString();
	}

}
