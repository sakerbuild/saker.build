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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiConsumer;

class FakeSortedMap<K, V> extends AbstractMap<K, V> implements SortedMap<K, V> {
	private final Map<K, V> sorted;
	private final Comparator<? super K> comparator;

	public FakeSortedMap(Map<K, V> sorted, Comparator<? super K> comparator) {
		this.sorted = sorted;
		this.comparator = comparator;
	}

	public FakeSortedMap(Map<K, V> sorted) {
		this(sorted, null);
	}

	@Override
	public int size() {
		return sorted.size();
	}

	@Override
	public boolean isEmpty() {
		return sorted.isEmpty();
	}

	@Override
	public Comparator<? super K> comparator() {
		return comparator;
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		sorted.forEach(action);
	}

	@Override
	public SortedMap<K, V> subMap(K fromKey, K toKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SortedMap<K, V> headMap(K toKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SortedMap<K, V> tailMap(K fromKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public K firstKey() {
		return sorted.keySet().iterator().next();
	}

	@Override
	public K lastKey() {
		if (sorted.isEmpty()) {
			throw new NoSuchElementException();
		}
		for (Iterator<K> it = sorted.keySet().iterator();;) {
			K k = it.next();
			if (!it.hasNext()) {
				return k;
			}
		}
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new FakeEntrySet();
	}

	protected Iterator<Entry<K, V>> entryIterator() {
		return sorted.entrySet().iterator();
	}

	private final class FakeEntrySet extends AbstractSet<Entry<K, V>> {
		@Override
		public int size() {
			return FakeSortedMap.this.size();
		}

		@Override
		public boolean isEmpty() {
			return FakeSortedMap.this.isEmpty();
		}

		@Override
		public Iterator<Entry<K, V>> iterator() {
			return entryIterator();
		}

	}
}
