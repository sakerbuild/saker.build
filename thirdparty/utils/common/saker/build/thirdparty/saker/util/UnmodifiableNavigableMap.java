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
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

class UnmodifiableNavigableMap<K, V> implements NavigableMap<K, V>, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableMap<K, ? extends V> map;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableNavigableMap() {
	}

	public UnmodifiableNavigableMap(NavigableMap<K, ? extends V> map) {
		this.map = map;
	}

	@Override
	public Comparator<? super K> comparator() {
		return map.comparator();
	}

	@Override
	public K firstKey() {
		return map.firstKey();
	}

	@Override
	public K lastKey() {
		return map.lastKey();
	}

	@Override
	public Set<K> keySet() {
		return new UnmodifiableSet<>(map.keySet());
	}

	@Override
	public Collection<V> values() {
		return new UnmodifiableCollection<>(map.values());
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new UnmodifiableMapEntrySet<>(map.entrySet());
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public V get(Object key) {
		return map.get(key);
	}

	@Override
	public V put(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Entry<K, V> lowerEntry(K key) {
		Entry<K, ? extends V> result = map.lowerEntry(key);
		if (result != null) {
			return ImmutableUtils.unmodifiableMapEntry(result);
		}
		return null;
	}

	@Override
	public K lowerKey(K key) {
		return map.lowerKey(key);
	}

	@Override
	public Entry<K, V> floorEntry(K key) {
		Entry<K, ? extends V> result = map.floorEntry(key);
		if (result != null) {
			return ImmutableUtils.unmodifiableMapEntry(result);
		}
		return null;
	}

	@Override
	public K floorKey(K key) {
		return map.floorKey(key);
	}

	@Override
	public Entry<K, V> ceilingEntry(K key) {
		Entry<K, ? extends V> result = map.ceilingEntry(key);
		if (result != null) {
			return ImmutableUtils.unmodifiableMapEntry(result);
		}
		return null;
	}

	@Override
	public K ceilingKey(K key) {
		return map.ceilingKey(key);
	}

	@Override
	public Entry<K, V> higherEntry(K key) {
		Entry<K, ? extends V> result = map.higherEntry(key);
		if (result != null) {
			return ImmutableUtils.unmodifiableMapEntry(result);
		}
		return null;
	}

	@Override
	public K higherKey(K key) {
		return map.higherKey(key);
	}

	@Override
	public Entry<K, V> firstEntry() {
		Entry<K, ? extends V> result = map.firstEntry();
		if (result != null) {
			return ImmutableUtils.unmodifiableMapEntry(result);
		}
		return null;
	}

	@Override
	public Entry<K, V> lastEntry() {
		Entry<K, ? extends V> result = map.lastEntry();
		if (result != null) {
			return ImmutableUtils.unmodifiableMapEntry(result);
		}
		return null;
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
		return new UnmodifiableNavigableMap<>(map.descendingMap());
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		return new UnmodifiableNavigableSet<>(map.navigableKeySet());
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		return new UnmodifiableNavigableSet<>(map.descendingKeySet());
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return new UnmodifiableNavigableMap<>(map.subMap(fromKey, fromInclusive, toKey, toInclusive));
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return new UnmodifiableNavigableMap<>(map.headMap(toKey, inclusive));
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return new UnmodifiableNavigableMap<>(map.tailMap(fromKey, inclusive));
	}

	@Override
	public SortedMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, false);
	}

	@Override
	public SortedMap<K, V> headMap(K toKey) {
		return headMap(toKey, false);
	}

	@Override
	public SortedMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return (V) ((Map) map).getOrDefault(key, defaultValue);
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		map.forEach(action);
	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V putIfAbsent(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V replace(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(map);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		map = (NavigableMap<K, ? extends V>) in.readObject();
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return map.equals(o);
	}

	@Override
	public String toString() {
		return map.toString();
	}
}
