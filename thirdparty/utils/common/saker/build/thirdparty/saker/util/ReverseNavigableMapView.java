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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Function;

abstract class ReverseNavigableMapView<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Externalizable {
	private static final long serialVersionUID = 1L;

	protected NavigableMap<K, V> map;

	/**
	 * For {@link Externalizable}.
	 */
	public ReverseNavigableMapView() {
	}

	public ReverseNavigableMapView(NavigableMap<K, V> map) {
		this.map = map;
	}

	@Override
	public Comparator<? super K> comparator() {
		return Collections.reverseOrder(map.comparator());
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		return map;
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		return map.navigableKeySet();
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		return map.descendingKeySet();
	}

	@Override
	public Set<K> keySet() {
		return navigableKeySet();
	}

	@Override
	public abstract Set<Entry<K, V>> entrySet();

	@Override
	public abstract Collection<V> values();

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public void clear() {
		map.clear();
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
	public K firstKey() {
		return map.lastKey();
	}

	@Override
	public K lastKey() {
		return map.firstKey();
	}

	@Override
	public Entry<K, V> firstEntry() {
		return map.lastEntry();
	}

	@Override
	public Entry<K, V> lastEntry() {
		return map.firstEntry();
	}

	@Override
	public Entry<K, V> lowerEntry(K key) {
		return map.higherEntry(key);
	}

	@Override
	public K lowerKey(K key) {
		return map.higherKey(key);
	}

	@Override
	public Entry<K, V> floorEntry(K key) {
		return map.ceilingEntry(key);
	}

	@Override
	public K floorKey(K key) {
		return map.ceilingKey(key);
	}

	@Override
	public Entry<K, V> ceilingEntry(K key) {
		return map.floorEntry(key);
	}

	@Override
	public K ceilingKey(K key) {
		return map.floorKey(key);
	}

	@Override
	public Entry<K, V> higherEntry(K key) {
		return map.lowerEntry(key);
	}

	@Override
	public K higherKey(K key) {
		return map.lowerKey(key);
	}

	@Override
	public Entry<K, V> pollFirstEntry() {
		return map.pollLastEntry();
	}

	@Override
	public Entry<K, V> pollLastEntry() {
		return map.pollFirstEntry();
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return map.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap();
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return map.tailMap(toKey, inclusive).descendingMap();
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return map.headMap(fromKey, inclusive).descendingMap();
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

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return map.getOrDefault(key, defaultValue);
	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		map.replaceAll(function);
	}

	@Override
	public V putIfAbsent(K key, V value) {
		return map.putIfAbsent(key, value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return map.remove(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return map.replace(key, oldValue, newValue);
	}

	@Override
	public V replace(K key, V value) {
		return map.replace(key, value);
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		return map.computeIfAbsent(key, mappingFunction);
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return map.computeIfPresent(key, remappingFunction);
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return map.compute(key, remappingFunction);
	}

	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return map.merge(key, value, remappingFunction);
	}

	@Override
	public V get(Object key) {
		return map.get(key);
	}

	@Override
	public V put(K key, V value) {
		return map.put(key, value);
	}

	@Override
	public V remove(Object key) {
		return map.remove(key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		map.putAll(m);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(map);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		map = (NavigableMap<K, V>) in.readObject();
	}

}
