package saker.build.thirdparty.saker.util;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;

/**
 * Pseudo-{@link Map} implementation that is backed by a set and dynamically generates the entries for it based on an
 * element value.
 * <p>
 * Works exactly the same way as {@link SetTransformingSortedMap}, but also implements {@link NavigableMap} as well.
 * 
 * @param <E>
 *            The element type of the set.
 * @param <K>
 *            The key type of this map.
 * @param <V>
 *            The value type of this map.
 */
public abstract class SetTransformingNavigableMap<E, K, V> extends SetTransformingSortedMap<E, K, V>
		implements NavigableMap<K, V> {
	/**
	 * Creates a new instance with the given set.
	 * 
	 * @param set
	 *            The subject set.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public SetTransformingNavigableMap(Set<? extends E> set) throws NullPointerException {
		super(set);
	}

	/**
	 * Creates a new instance with the given set and comparator.
	 * 
	 * @param set
	 *            The subject set.
	 * @param comparator
	 *            The comparator.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public SetTransformingNavigableMap(Set<? extends E> set, Comparator<? super K> comparator)
			throws NullPointerException {
		super(set, comparator);
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
	public Entry<K, V> lowerEntry(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public K lowerKey(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> floorEntry(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public K floorKey(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> ceilingEntry(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public K ceilingKey(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> higherEntry(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public K higherKey(K key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> firstEntry() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> lastEntry() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> pollFirstEntry() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> pollLastEntry() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		throw new UnsupportedOperationException();
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

}
