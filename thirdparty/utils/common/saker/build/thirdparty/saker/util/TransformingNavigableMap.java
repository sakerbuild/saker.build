package saker.build.thirdparty.saker.util;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;

/**
 * Pseudo-{@link Map} implementation that dynamically generates the entries for it based on the entries of a subject
 * map.
 * <p>
 * Works exactly the same way as {@link TransformingSortedMap}, but also implements {@link NavigableSet} as well.
 * 
 * @param <MK>
 *            The key type of the source map.
 * @param <MV>
 *            The value type of the source map.
 * @param <K>
 *            The key type of this map.
 * @param <V>
 *            The value type of this map.
 */
public abstract class TransformingNavigableMap<MK, MV, K, V> extends TransformingSortedMap<MK, MV, K, V>
		implements NavigableMap<K, V> {
	/**
	 * Creates a new instance with the given map.
	 * 
	 * @param map
	 *            The subject map.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public TransformingNavigableMap(Map<? extends MK, ? extends MV> map) throws NullPointerException {
		super(map);
	}

	/**
	 * Creates a new instance with the given map and comparator.
	 * 
	 * @param map
	 *            The subject map.
	 * @param comparator
	 *            The comparator.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public TransformingNavigableMap(Map<? extends MK, ? extends MV> map, Comparator<? super K> comparator)
			throws NullPointerException {
		super(map, comparator);
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

}
