package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

class EmptySortedMap<K, V> implements SortedMap<K, V>, Externalizable {
	private static final long serialVersionUID = 1L;

	static final SortedMap<?, ?> EMPTY_SORTED_MAP = new EmptySortedMap<>();

	public EmptySortedMap() {
	}

	@Override
	public Comparator<? super K> comparator() {
		return null;
	}

	@Override
	public final K firstKey() {
		throw new NoSuchElementException();
	}

	@Override
	public final K lastKey() {
		throw new NoSuchElementException();
	}

	@Override
	public final Set<K> keySet() {
		return Collections.emptySet();
	}

	@Override
	public final Collection<V> values() {
		return Collections.emptySet();
	}

	@Override
	public final Set<Entry<K, V>> entrySet() {
		return Collections.emptySet();
	}

	@Override
	public final int size() {
		return 0;
	}

	@Override
	public final boolean isEmpty() {
		return true;
	}

	@Override
	public final boolean containsKey(Object key) {
		return false;
	}

	@Override
	public final boolean containsValue(Object value) {
		return false;
	}

	@Override
	public final V get(Object key) {
		return null;
	}

	@Override
	public final V put(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final V remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final SortedMap<K, V> subMap(K fromKey, K toKey) {
		return this;
	}

	@Override
	public final SortedMap<K, V> headMap(K toKey) {
		return this;
	}

	@Override
	public final SortedMap<K, V> tailMap(K fromKey) {
		return this;
	}

	@Override
	public final String toString() {
		return "{}";
	}

	@Override
	public final int hashCode() {
		return 0;
	}

	@Override
	public final boolean equals(Object obj) {
		if (obj == this)
			return true;

		if (!(obj instanceof Map))
			return false;
		return ((Map<?, ?>) obj).isEmpty();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	private Object readResolve() {
		return EMPTY_SORTED_MAP;
	}
}
