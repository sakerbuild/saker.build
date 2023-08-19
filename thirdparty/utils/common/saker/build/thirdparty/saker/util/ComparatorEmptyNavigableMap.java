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
import java.util.NavigableMap;
import java.util.NavigableSet;

class ComparatorEmptyNavigableMap<K, V> extends EmptyNavigableMap<K, V> {
	private static final long serialVersionUID = 1L;

	protected Comparator<? super K> comparator;

	/**
	 * For {@link Externalizable}.
	 */
	public ComparatorEmptyNavigableMap() {
	}

	public ComparatorEmptyNavigableMap(Comparator<? super K> comparator) {
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super K> comparator() {
		return comparator;
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		return new DescendingComparatorEmptyNavigableMap<>(this);
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
		comparator = (Comparator<? super K>) in.readObject();
	}

	private static final class DescendingComparatorEmptyNavigableMap<K, V> extends EmptyNavigableMap<K, V> {
		private static final long serialVersionUID = 1L;

		private ComparatorEmptyNavigableMap<K, V> outer;

		//cache field
		private transient Comparator<? super K> reverseComparator;

		/**
		 * For {@link Externalizable}.
		 */
		public DescendingComparatorEmptyNavigableMap() {
		}

		private DescendingComparatorEmptyNavigableMap(ComparatorEmptyNavigableMap<K, V> outer) {
			this.outer = outer;
		}

		@Override
		public Comparator<? super K> comparator() {
			Comparator<? super K> res = reverseComparator;
			if (res == null) {
				res = Collections.reverseOrder(outer.comparator);
				reverseComparator = res;
			}
			return res;
		}

		@Override
		public NavigableSet<K> navigableKeySet() {
			return outer.descendingKeySet();
		}

		@Override
		public NavigableMap<K, V> descendingMap() {
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
			this.outer = (ComparatorEmptyNavigableMap<K, V>) in.readObject();
		}
	}
}
