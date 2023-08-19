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
import java.util.NavigableSet;

class EmptyNavigableSet<E> extends EmptySortedSet<E> implements NavigableSet<E> {
	private static final long serialVersionUID = 1L;

	static final NavigableSet<?> EMPTY_NAVIGABLE_SET = new EmptyNavigableSet<>();

	public EmptyNavigableSet() {
	}

	@Override
	public Comparator<? super E> comparator() {
		return null;
	}

	@Override
	public final E lower(E e) {
		return null;
	}

	@Override
	public final E floor(E e) {
		return null;
	}

	@Override
	public final E ceiling(E e) {
		return null;
	}

	@Override
	public final E higher(E e) {
		return null;
	}

	@Override
	public final E pollFirst() {
		return null;
	}

	@Override
	public final E pollLast() {
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public NavigableSet<E> descendingSet() {
		return (NavigableSet<E>) ReverseEmptyNavigableSet.INSTANCE;
	}

	@Override
	public final Iterator<E> descendingIterator() {
		return Collections.emptyIterator();
	}

	@Override
	public final NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return this;
	}

	@Override
	public final NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return this;
	}

	@Override
	public final NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	private Object readResolve() {
		return EMPTY_NAVIGABLE_SET;
	}

	static class ReverseEmptyNavigableSet<E> extends EmptyNavigableSet<E> {
		private static final long serialVersionUID = 1L;

		static final NavigableSet<?> INSTANCE = new ReverseEmptyNavigableSet<>();

		/**
		 * For {@link Externalizable}.
		 */
		public ReverseEmptyNavigableSet() {
		}

		@Override
		public Comparator<? super E> comparator() {
			return Collections.reverseOrder();
		}

		@SuppressWarnings("unchecked")
		@Override
		public NavigableSet<E> descendingSet() {
			return (NavigableSet<E>) EmptyNavigableSet.EMPTY_NAVIGABLE_SET;
		}

		private Object readResolve() {
			return INSTANCE;
		}
	}
}
