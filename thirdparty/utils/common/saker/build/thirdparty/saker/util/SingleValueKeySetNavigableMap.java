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
import java.util.NavigableMap;
import java.util.NavigableSet;

class SingleValueKeySetNavigableMap<K, V> extends SingleValueKeySetSortedMap<K, V, NavigableSet<K>>
		implements NavigableMap<K, V> {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public SingleValueKeySetNavigableMap() {
	}

	public SingleValueKeySetNavigableMap(NavigableSet<K> set, V value) {
		super(set, value);
	}

	@Override
	public Entry<K, V> lowerEntry(K key) {
		K l = set.lower(key);
		if (l == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(l, value);
	}

	@Override
	public K lowerKey(K key) {
		return set.lower(key);
	}

	@Override
	public Entry<K, V> floorEntry(K key) {
		K l = set.floor(key);
		if (l == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(l, value);
	}

	@Override
	public K floorKey(K key) {
		return set.floor(key);
	}

	@Override
	public Entry<K, V> ceilingEntry(K key) {
		K l = set.ceiling(key);
		if (l == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(l, value);
	}

	@Override
	public K ceilingKey(K key) {
		return set.ceiling(key);
	}

	@Override
	public Entry<K, V> higherEntry(K key) {
		K l = set.higher(key);
		if (l == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(l, value);
	}

	@Override
	public K higherKey(K key) {
		return set.higher(key);
	}

	@Override
	public Entry<K, V> firstEntry() {
		return ImmutableUtils.makeImmutableMapEntry(set.first(), value);
	}

	@Override
	public Entry<K, V> lastEntry() {
		return ImmutableUtils.makeImmutableMapEntry(set.last(), value);
	}

	@Override
	public Entry<K, V> pollFirstEntry() {
		K f = set.pollFirst();
		if (f == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(f, value);
	}

	@Override
	public Entry<K, V> pollLastEntry() {
		K f = set.pollLast();
		if (f == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(f, value);
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		return new SingleValueKeySetNavigableMap<>(set.descendingSet(), value);
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		return set;
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		return set.descendingSet();
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return new SingleValueKeySetNavigableMap<>(set.subSet(fromKey, fromInclusive, toKey, toInclusive), value);
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return new SingleValueKeySetNavigableMap<>(set.headSet(toKey, inclusive), value);
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return new SingleValueKeySetNavigableMap<>(set.tailSet(fromKey, inclusive), value);
	}

}
