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
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiConsumer;

class FakeSortedEntryMap<K, V> extends AbstractMap<K, V> implements SortedMap<K, V> {
	private final Iterable<? extends Entry<? extends K, ? extends V>> sorted;
	private final int size;
	private final Comparator<? super K> comparator;

	public FakeSortedEntryMap(Iterable<? extends Entry<? extends K, ? extends V>> sorted, int size,
			Comparator<? super K> comparator) {
		this.sorted = sorted;
		this.size = size;
		this.comparator = comparator;
	}

	public FakeSortedEntryMap(Iterable<? extends Entry<? extends K, ? extends V>> sorted, int size) {
		this(sorted, size, null);
	}

	public FakeSortedEntryMap(Collection<? extends Entry<? extends K, ? extends V>> sorted) {
		this(sorted, null);
	}

	public FakeSortedEntryMap(Collection<? extends Entry<? extends K, ? extends V>> sorted,
			Comparator<? super K> comparator) {
		this(sorted, sorted.size(), comparator);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	@Override
	public Comparator<? super K> comparator() {
		return comparator;
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		sorted.forEach(e -> action.accept(e.getKey(), e.getValue()));
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
		throw new UnsupportedOperationException();
	}

	@Override
	public K lastKey() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new FakeEntrySet();
	}

	private final class FakeEntrySet extends AbstractSet<Entry<K, V>> {
		@Override
		public int size() {
			return FakeSortedEntryMap.this.size();
		}

		@Override
		public boolean isEmpty() {
			return FakeSortedEntryMap.this.isEmpty();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Iterator<Entry<K, V>> iterator() {
			//incorrect unchecked cast, but it is okay, as the entry is only read, and never modified
			return (Iterator<Entry<K, V>>) sorted.iterator();
		}

	}
}
