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
import java.util.NavigableSet;

class ComparatorEmptyNavigableSet<E> extends EmptyNavigableSet<E> {

	private static final long serialVersionUID = 1L;

	protected Comparator<? super E> comparator;

	/**
	 * For {@link Externalizable}.
	 */
	public ComparatorEmptyNavigableSet() {
	}

	public ComparatorEmptyNavigableSet(Comparator<? super E> comparator) {
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super E> comparator() {
		return comparator;
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return new DescendingComparatorEmptyNavigableSet<>(this);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(comparator);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		comparator = (Comparator<? super E>) in.readObject();
	}

	private static final class DescendingComparatorEmptyNavigableSet<E> extends EmptyNavigableSet<E> {
		private static final long serialVersionUID = 1L;

		private ComparatorEmptyNavigableSet<E> outer;

		//cache field
		private transient Comparator<? super E> reverseComparator;

		/**
		 * For {@link Externalizable}.
		 */
		public DescendingComparatorEmptyNavigableSet() {
		}

		private DescendingComparatorEmptyNavigableSet(ComparatorEmptyNavigableSet<E> outer) {
			this.outer = outer;
		}

		@Override
		public Comparator<? super E> comparator() {
			Comparator<? super E> res = reverseComparator;
			if (res == null) {
				res = Collections.reverseOrder(outer.comparator);
				reverseComparator = res;
			}
			return res;
		}

		@Override
		public NavigableSet<E> descendingSet() {
			return outer;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(outer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			this.outer = (ComparatorEmptyNavigableSet<E>) in.readObject();
		}
	}
}
