package saker.build.thirdparty.saker.util;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Pseudo-{@link Map} implementation that is backed by a set and dynamically generates the entries for it based on an
 * element value.
 * <p>
 * This map works the same way as {@link SetTransformingMap}, but also implements {@link SortedMap}.
 * <p>
 * A comparator can be specified during the construction of this map, which will be reported when {@link #comparator()}
 * is called. When this map is used as the argument to a constructor of a sorted map (E.g.
 * {@link TreeMap#TreeMap(SortedMap)}), this will cause the constructed map to have the same ordering as this map.
 * <p>
 * <b>Important:</b> Implementations should ensure that the transformed keys are ordered by the comparator of this
 * constructed map. Violating this may result in undefined behaviour in some implementations.
 * <p>
 * The use-case for this map is the same as for {@link SetTransformingMap}. See the documentation of that class for more
 * information.
 * 
 * @param <E>
 *            The element type of the set.
 * @param <K>
 *            The key type of this map.
 * @param <V>
 *            The value type of this map.
 */
public abstract class SetTransformingSortedMap<E, K, V> extends SetTransformingMap<E, K, V> implements SortedMap<K, V> {
	/**
	 * The comparator that this map is ordered by.
	 */
	protected final Comparator<? super K> comparator;

	/**
	 * Creates a new instance with the given set.
	 * 
	 * @param set
	 *            The subject set.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public SetTransformingSortedMap(Set<? extends E> set) throws NullPointerException {
		this(set, null);
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
	public SetTransformingSortedMap(Set<? extends E> set, Comparator<? super K> comparator)
			throws NullPointerException {
		super(set);
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super K> comparator() {
		return comparator;
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
