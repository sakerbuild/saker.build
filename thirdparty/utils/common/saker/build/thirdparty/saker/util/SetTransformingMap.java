package saker.build.thirdparty.saker.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Pseudo-{@link Map} implementation that is backed by a set and dynamically generates the entries for it based on an
 * element value.
 * <p>
 * This map is immutable, and designed to provide a map implementation that is only used to iterate over its entries.
 * Each entry in this map is dynamically generated based on an entry in the underlying set.
 * <p>
 * Any method that is not the {@link #size()}, {@link #isEmpty()}, {@link #forEach(BiConsumer)}, {@link #values()},
 * {@link #keySet()}, {@link #entrySet()} and their {@link Iterable#iterator() iterator()} functions, may throw an
 * {@link UnsupportedOperationException} any time.
 * <p>
 * <b>Important:</b> Implementations should ensure that the transformed keys still stay unique in the map, as they was
 * in the subject set. Violating this may result in undefined behaviour in some implementations.
 * <p>
 * An use-case for this kind of map is to create a new {@link Map} with the given entries without pre-allocating the
 * transformed entries beforehand.
 * <p>
 * Example: <br>
 * A new map is created from a set of strings that have the parsed integers as values for it.
 * 
 * <pre>
 * Set&lt;String&gt; set = ...;
 * Map&lt;String, Integer&gt; nmap = new TreeMap&lt;&gt;(new SetTransformingMap&lt;String, String, Integer&gt;(set) {
 * 	&#64;Override
 * 	protected Map.Entry&lt;String, Integer&gt; transformEntry(String e) {
 * 		return ImmutableUtils.makeImmutableMapEntry(e, Integer.parseInt(e);
 * 	}
 * });
 * </pre>
 * 
 * Constructing a map in this way instead of calling {@link Map#put(Object, Object)} for every entry can be more
 * efficient, as new maps can allocate or construct their instances more efficiently.
 * 
 * @param <E>
 *            The element type of the set.
 * @param <K>
 *            The key type of this map.
 * @param <V>
 *            The value type of this map.
 */
public abstract class SetTransformingMap<E, K, V> extends AbstractMap<K, V> {
	/**
	 * The backing set of the transforming map.
	 */
	protected final Set<? extends E> set;

	/**
	 * Creates a new instance with the given set.
	 * 
	 * @param set
	 *            The subject set.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public SetTransformingMap(Set<? extends E> set) throws NullPointerException {
		Objects.requireNonNull(set, "set");
		this.set = set;
	}

	/**
	 * Transforms the set element to the map entry.
	 * 
	 * @param e
	 *            The element to transform.
	 * @return The transformed entry.
	 * @see ImmutableUtils#makeImmutableMapEntry(Object, Object).
	 */
	protected abstract Map.Entry<K, V> transformEntry(E e);

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K, V>>() {
			@Override
			public Iterator<Entry<K, V>> iterator() {
				Iterator<? extends E> it = set.iterator();
				return new Iterator<Map.Entry<K, V>>() {
					@Override
					public boolean hasNext() {
						return it.hasNext();
					}

					@Override
					public Entry<K, V> next() {
						E next = it.next();
						return transformEntry(next);
					}
				};
			}

			@Override
			public int size() {
				return set.size();
			}
		};
	}
}
