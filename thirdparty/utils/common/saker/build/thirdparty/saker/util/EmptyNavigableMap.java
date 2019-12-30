package saker.build.thirdparty.saker.util;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.NavigableSet;

class EmptyNavigableMap<K, V> extends EmptySortedMap<K, V> implements NavigableMap<K, V> {
	private static final long serialVersionUID = 1L;

	static final NavigableMap<?, ?> EMPTY_NAVIGABLE_MAP = new EmptyNavigableMap<>();

	public EmptyNavigableMap() {
	}

	@Override
	public Comparator<? super K> comparator() {
		return null;
	}

	@Override
	public final Entry<K, V> lowerEntry(K key) {
		return null;
	}

	@Override
	public final K lowerKey(K key) {
		return null;
	}

	@Override
	public final Entry<K, V> floorEntry(K key) {
		return null;
	}

	@Override
	public final K floorKey(K key) {
		return null;
	}

	@Override
	public final Entry<K, V> ceilingEntry(K key) {
		return null;
	}

	@Override
	public final K ceilingKey(K key) {
		return null;
	}

	@Override
	public final Entry<K, V> higherEntry(K key) {
		return null;
	}

	@Override
	public final K higherKey(K key) {
		return null;
	}

	@Override
	public final Entry<K, V> firstEntry() {
		return null;
	}

	@Override
	public final Entry<K, V> lastEntry() {
		return null;
	}

	@Override
	public final Entry<K, V> pollFirstEntry() {
		return null;
	}

	@Override
	public final Entry<K, V> pollLastEntry() {
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final NavigableMap<K, V> descendingMap() {
		return ImmutableUtils.emptyNavigableMap(Collections.reverseOrder(comparator()));
	}

	@Override
	public final NavigableSet<K> navigableKeySet() {
		return ImmutableUtils.emptyNavigableSet(comparator());
	}

	@Override
	public final NavigableSet<K> descendingKeySet() {
		return ImmutableUtils.emptyNavigableSet(Collections.reverseOrder(comparator()));
	}

	@Override
	public final NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return this;
	}

	@Override
	public final NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return this;
	}

	@Override
	public final NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	private Object readResolve() {
		return EMPTY_NAVIGABLE_MAP;
	}
}
