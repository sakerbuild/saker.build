package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

class UnmodifiableSortedMap<K, V> implements SortedMap<K, V>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SortedMap<K, ? extends V> map;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableSortedMap() {
	}

	public UnmodifiableSortedMap(SortedMap<K, ? extends V> map) {
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
	public SortedMap<K, V> subMap(K fromKey, K toKey) {
		return new UnmodifiableSortedMap<>(map.subMap(fromKey, toKey));
	}

	@Override
	public SortedMap<K, V> headMap(K toKey) {
		return new UnmodifiableSortedMap<>(map.headMap(toKey));
	}

	@Override
	public SortedMap<K, V> tailMap(K fromKey) {
		return new UnmodifiableSortedMap<>(map.tailMap(fromKey));
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
		map = (SortedMap<K, ? extends V>) in.readObject();
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
