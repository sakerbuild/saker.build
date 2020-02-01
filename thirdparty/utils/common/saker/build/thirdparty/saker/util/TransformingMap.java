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
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Pseudo-{@link Map} implementation that dynamically generates the entries for it based on the entries of a subject
 * map.
 * <p>
 * This map is immutable, and designed to provide a map implementation that is only used to iterate over its entries.
 * Each entry in this map is dynamically generated based on an entry in the underlying map.
 * <p>
 * Any method that is not the {@link #size()}, {@link #isEmpty()}, {@link #forEach(BiConsumer)}, {@link #values()},
 * {@link #keySet()}, {@link #entrySet()} and their {@link Iterable#iterator() iterator()} functions, may throw an
 * {@link UnsupportedOperationException} any time.
 * <p>
 * <b>Important:</b> Implementations should ensure that the transformed keys still stay unique in the map, as they was
 * in the subject map. Violating this may result in undefined behaviour in some implementations.
 * <p>
 * An use-case for this kind of map is to create a new {@link Map} with the given entries without pre-allocating the
 * transformed entries beforehand.
 * <p>
 * Example: <br>
 * 
 * <pre>
 * Map&lt;Integer, Integer&gt; ints = ...;
 * Map&lt;Integer, String&gt; nmap = new TreeMap&lt;&gt;(new TransformingMap&lt;Integer, Integer, Integer, String&gt;(ints) {
 * 	&#64;Override
 * 	protected Entry&lt;Integer, String&gt; transformEntry(Integer key, Integer value) {
 * 		return ImmutableUtils.makeImmutableMapEntry(key + value, Integer.toString(key * value));
 * 	}
 * });
 * </pre>
 * 
 * Constructing a map in this way instead of calling {@link Map#put(Object, Object)} for every entry can be more
 * efficient, as new maps can allocate or construct their instances more efficiently.
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
public abstract class TransformingMap<MK, MV, K, V> extends AbstractMap<K, V> {
	/**
	 * The backing map of the transforming map.
	 */
	protected final Map<? extends MK, ? extends MV> map;

	/**
	 * Creates a new instance with the given map.
	 * 
	 * @param map
	 *            The subject map.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public TransformingMap(Map<? extends MK, ? extends MV> map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		this.map = map;
	}

	/**
	 * Transforms the source entry key-value pair to the map entry.
	 *
	 * @param key
	 *            The source entry key.
	 * @param value
	 *            The source entry value.
	 * @return The transformed entry.
	 * @see ImmutableUtils#makeImmutableMapEntry(Object, Object).
	 */
	protected abstract Map.Entry<K, V> transformEntry(MK key, MV value);

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K, V>>() {
			@Override
			public Iterator<Entry<K, V>> iterator() {
				Iterator<? extends Entry<? extends MK, ? extends MV>> it = map.entrySet().iterator();
				return new Iterator<Map.Entry<K, V>>() {

					@Override
					public boolean hasNext() {
						return it.hasNext();
					}

					@Override
					public Entry<K, V> next() {
						Entry<? extends MK, ? extends MV> n = it.next();
						MK key = n.getKey();
						MV value = n.getValue();
						return transformEntry(key, value);
					}
				};
			}

			@Override
			public int size() {
				return map.size();
			}
		};
	}

}
