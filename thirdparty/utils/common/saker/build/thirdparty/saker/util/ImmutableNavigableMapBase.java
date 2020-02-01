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
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

abstract class ImmutableNavigableMapBase<K, V> implements NavigableMap<K, V> {
	protected abstract int binarySearch(K k);

	protected abstract Entry<K, V> entryAt(int idx);

	protected K keyAt(int idx) {
		return entryAt(idx).getKey();
	}

	protected V valueAt(int idx) {
		return entryAt(idx).getValue();
	}

	protected final int lowerIndex(K e) {
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

	protected final int floorIndex(K e) {
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

	protected final int ceilingIndex(K e) {
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

	protected final int higherIndex(K e) {
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

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public final K firstKey() {
		if (isEmpty()) {
			throw new NoSuchElementException();
		}
		return keyAt(0);
	}

	@Override
	public final K lastKey() {
		if (isEmpty()) {
			throw new NoSuchElementException();
		}
		return keyAt(size() - 1);
	}

	@Override
	public final boolean containsKey(Object key) {
		@SuppressWarnings("unchecked")
		int idx = binarySearch((K) key);
		return idx >= 0;
	}

	@Override
	public final V get(Object key) {
		@SuppressWarnings("unchecked")
		int idx = binarySearch((K) key);
		if (idx < 0) {
			return null;
		}
		return valueAt(idx);
	}

	@Override
	public final K lowerKey(K key) {
		int idx = lowerIndex(key);
		return idx < 0 ? null : keyAt(idx);
	}

	@Override
	public final K floorKey(K key) {
		int idx = floorIndex(key);
		return idx < 0 ? null : keyAt(idx);
	}

	@Override
	public final K ceilingKey(K key) {
		int idx = ceilingIndex(key);
		return idx < 0 ? null : keyAt(idx);
	}

	@Override
	public final K higherKey(K key) {
		int idx = higherIndex(key);
		return idx < 0 ? null : keyAt(idx);
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new EntrySet();
	}

	private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
		@Override
		public Iterator<Entry<K, V>> iterator() {
			int size = this.size();
			return new Iterator<Map.Entry<K, V>>() {
				private int idx = 0;

				@Override
				public boolean hasNext() {
					return idx < size;
				}

				@Override
				public Entry<K, V> next() {
					return entryAt(idx++);
				}
			};
		}

		@Override
		public int size() {
			return ImmutableNavigableMapBase.this.size();
		}
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		return descendingMap().navigableKeySet();
	}

	@Override
	public final Set<K> keySet() {
		return navigableKeySet();
	}

	@Override
	public final Entry<K, V> lowerEntry(K key) {
		int idx = lowerIndex(key);
		return idx < 0 ? null : entryAt(idx);
	}

	@Override
	public final Entry<K, V> floorEntry(K key) {
		int idx = floorIndex(key);
		return idx < 0 ? null : entryAt(idx);
	}

	@Override
	public final Entry<K, V> ceilingEntry(K key) {
		int idx = ceilingIndex(key);
		return idx < 0 ? null : entryAt(idx);
	}

	@Override
	public final Entry<K, V> higherEntry(K key) {
		int idx = higherIndex(key);
		return idx < 0 ? null : entryAt(idx);
	}

	@Override
	public final Entry<K, V> firstEntry() {
		if (isEmpty()) {
			throw new NoSuchElementException();
		}
		return entryAt(0);
	}

	@Override
	public final Entry<K, V> lastEntry() {
		if (isEmpty()) {
			throw new NoSuchElementException();
		}
		return entryAt(size() - 1);
	}

	@Override
	public final SortedMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, false);
	}

	@Override
	public final SortedMap<K, V> headMap(K toKey) {
		return headMap(toKey, false);
	}

	@Override
	public final SortedMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Override
	public final V put(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final Entry<K, V> pollFirstEntry() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final Entry<K, V> pollLastEntry() {
		throw new UnsupportedOperationException();
	}

	//equals, hashcode, tostring is based on AbstractMap implementation

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (!(o instanceof Map))
			return false;
		Map<?, ?> m = (Map<?, ?>) o;
		if (m.size() != size())
			return false;

		try {
			for (Entry<K, V> e : entrySet()) {
				K key = e.getKey();
				V value = e.getValue();
				if (value == null) {
					if (!(m.get(key) == null && m.containsKey(key)))
						return false;
				} else {
					if (!value.equals(m.get(key)))
						return false;
				}
			}
		} catch (ClassCastException unused) {
			return false;
		} catch (NullPointerException unused) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int h = 0;
		for (Entry<K, V> entry : entrySet()) {
			h += entry.hashCode();
		}
		return h;
	}

	@Override
	public String toString() {
		Iterator<Entry<K, V>> i = entrySet().iterator();
		if (!i.hasNext())
			return "{}";

		StringBuilder sb = new StringBuilder();
		sb.append('{');
		for (;;) {
			Entry<K, V> e = i.next();
			K key = e.getKey();
			V value = e.getValue();
			sb.append(key == this ? "(this Map)" : key);
			sb.append('=');
			sb.append(value == this ? "(this Map)" : value);
			if (!i.hasNext())
				return sb.append('}').toString();
			sb.append(',').append(' ');
		}
	}
}
