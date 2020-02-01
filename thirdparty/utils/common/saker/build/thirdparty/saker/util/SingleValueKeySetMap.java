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
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

class SingleValueKeySetMap<K, V, S extends Set<K>> extends AbstractMap<K, V> implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected S set;
	protected V value;

	/**
	 * For {@link Externalizable}.
	 */
	public SingleValueKeySetMap() {
	}

	public SingleValueKeySetMap(S set, V value) {
		this.set = set;
		this.value = value;
	}

	@Override
	public final Set<K> keySet() {
		return set;
	}

	@Override
	public final Collection<V> values() {
		return new AbstractCollection<V>() {
			@Override
			public Iterator<V> iterator() {
				Iterator<? extends K> keyit = set.iterator();
				return new Iterator<V>() {
					@Override
					public boolean hasNext() {
						return keyit.hasNext();
					}

					@Override
					public V next() {
						keyit.next();
						return value;
					}

					@Override
					public void remove() {
						keyit.remove();
					}
				};
			}

			@Override
			public int size() {
				return set.size();
			}
		};
	}

	@Override
	public final Set<Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K, V>>() {
			@Override
			public Iterator<Entry<K, V>> iterator() {
				Iterator<? extends K> keyit = set.iterator();
				return new Iterator<Map.Entry<K, V>>() {

					@Override
					public boolean hasNext() {
						return keyit.hasNext();
					}

					@Override
					public Entry<K, V> next() {
						K k = keyit.next();
						return ImmutableUtils.makeImmutableMapEntry(k, value);
					}

					@Override
					public void remove() {
						keyit.remove();
					}
				};
			}

			@Override
			public int size() {
				return set.size();
			}
		};
	}

	@Override
	public final int size() {
		return set.size();
	}

	@Override
	public final boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public final boolean containsKey(Object key) {
		return set.contains(key);
	}

	@Override
	public final boolean containsValue(Object value) {
		return !set.isEmpty() && Objects.equals(this.value, value);
	}

	@Override
	public final V get(Object key) {
		return set.contains(key) ? this.value : null;
	}

	@Override
	public final V put(K key, V value) {
		if (value == this.value) {
			return set.add(key) ? null : value;
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public final V remove(Object key) {
		return set.remove(key) ? value : null;
	}

	@Override
	public final void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void clear() {
		set.clear();
	}

	@Override
	public final V getOrDefault(Object key, V defaultValue) {
		return set.contains(key) ? value : defaultValue;
	}

	@Override
	public final void forEach(BiConsumer<? super K, ? super V> action) {
		set.forEach(e -> action.accept(e, value));
	}

	@Override
	public final void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V putIfAbsent(K key, V value) {
		if (value == this.value) {
			return set.add(key) ? null : value;
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean remove(Object key, Object value) {
		if (value == this.value) {
			return set.remove(key);
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean replace(K key, V oldValue, V newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V replace(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(set);
		out.writeObject(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		set = (S) in.readObject();
		value = (V) in.readObject();
	}
}
