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
import java.util.Comparator;
import java.util.SortedMap;
import java.util.SortedSet;

class SingleValueKeySetSortedMap<K, V, S extends SortedSet<K>> extends SingleValueKeySetMap<K, V, S>
		implements SortedMap<K, V> {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public SingleValueKeySetSortedMap() {
	}

	public SingleValueKeySetSortedMap(S set, V value) {
		super(set, value);
	}

	@Override
	public final Comparator<? super K> comparator() {
		return set.comparator();
	}

	@Override
	public final K firstKey() {
		return set.first();
	}

	@Override
	public final K lastKey() {
		return set.last();
	}

	@Override
	public final SortedMap<K, V> subMap(K fromKey, K toKey) {
		return new SingleValueKeySetSortedMap<>(set.subSet(fromKey, toKey), value);
	}

	@Override
	public final SortedMap<K, V> headMap(K toKey) {
		return new SingleValueKeySetSortedMap<>(set.headSet(toKey), value);
	}

	@Override
	public final SortedMap<K, V> tailMap(K fromKey) {
		return new SingleValueKeySetSortedMap<>(set.tailSet(fromKey), value);
	}

}
