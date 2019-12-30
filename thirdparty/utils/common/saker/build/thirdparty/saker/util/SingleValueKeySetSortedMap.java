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
