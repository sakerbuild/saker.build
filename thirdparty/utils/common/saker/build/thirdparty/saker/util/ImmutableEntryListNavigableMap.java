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
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.function.BiConsumer;

import saker.build.thirdparty.saker.util.io.SerialUtils;

class ImmutableEntryListNavigableMap<K, V> extends ImmutableNavigableMapBase<K, V> implements Externalizable {
	private static final long serialVersionUID = 1L;

	private List<Entry<K, V>> entries;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableEntryListNavigableMap() {
	}

	protected ImmutableEntryListNavigableMap(List<Entry<K, V>> entries) {
		this.entries = entries;
	}

	/**
	 * <b>Entries must be immutable, list should not be modified, and should efficiently subList.</b>
	 */
	public static <K, V> NavigableMap<K, V> create(Comparator<? super K> comparator, List<Entry<K, V>> entries) {
		if (entries.isEmpty()) {
			return ImmutableUtils.emptyNavigableMap(comparator);
		}
		if (comparator == null) {
			return new ImmutableEntryListNavigableMap<>(entries);
		}
		return new ImmutableEntryListComparatorNavigableMap<>(entries, comparator);
	}

	/**
	 * <b>Entries must be immutable, list should not be modified, and should efficiently subList.</b>
	 */
	public static <K, V> NavigableMap<K, V> create(List<Entry<K, V>> entries) {
		if (entries.isEmpty()) {
			return Collections.emptyNavigableMap();
		}
		return new ImmutableEntryListNavigableMap<>(entries);
	}

	@Override
	public Comparator<? super K> comparator() {
		return null;
	}

	@Override
	protected final Entry<K, V> entryAt(int idx) {
		return entries.get(idx);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected final int binarySearch(K k) {
		//based on collections binarysearch
		Comparator<? super K> c = comparator();
		if (c == null) {
			c = (Comparator) Comparator.naturalOrder();
		}
		int low = 0;
		int high = size() - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			K midVal = keyAt(mid);
			int cmp = c.compare(midVal, k);

			if (cmp < 0)
				low = mid + 1;
			else if (cmp > 0)
				high = mid - 1;
			else
				return mid; // key found
		}
		return -(low + 1); // key not found
	}

	@Override
	public int size() {
		return entries.size();
	}

	@Override
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	@Override
	public Collection<V> values() {
		return new Values();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new EntrySet();
	}

	private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
		@Override
		public Iterator<Entry<K, V>> iterator() {
			return entries.iterator();
		}

		@Override
		public int size() {
			return ImmutableEntryListNavigableMap.this.size();
		}
	}

	private final class Values extends AbstractCollection<V> {
		@Override
		public Iterator<V> iterator() {
			Iterator<Entry<K, V>> it = entries.iterator();
			return new Iterator<V>() {

				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public V next() {
					return it.next().getValue();
				}
			};
		}

		@Override
		public int size() {
			return ImmutableEntryListNavigableMap.this.size();
		}
	}

	@Override
	public boolean containsValue(Object value) {
		if (value == null) {
			for (Entry<K, V> entry : entries) {
				if (entry.getValue() == null) {
					return true;
				}
			}
		} else {
			for (Entry<K, V> entry : entries) {
				if (value.equals(entry.getValue())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		return create(Collections.reverseOrder(comparator()), ObjectUtils.reversedList(entries));
	}

	@SuppressWarnings("unchecked")
	@Override
	public NavigableSet<K> navigableKeySet() {
		//XXX make this more efficient?
		Object[] keys = new Object[size()];
		int i = 0;
		for (Entry<K, V> e : entries) {
			keys[i++] = e.getKey();
		}
		return ImmutableListComparatorNavigableSet.create(comparator(),
				(List<K>) ImmutableUtils.asUnmodifiableArrayList(keys));
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		//XXX make this more efficient maybe?
		return navigableKeySet().descendingSet();
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		int fromidx = fromInclusive ? ceilingIndex(fromKey) : higherIndex(fromKey);
		if (fromidx < 0) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		int toidx = toInclusive ? floorIndex(toKey) : lowerIndex(toKey);
		if (toidx < 0) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		if (toidx < fromidx) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		return create(comparator(), entries.subList(fromidx, toidx + 1));
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		int idx = inclusive ? floorIndex(toKey) : lowerIndex(toKey);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		return create(comparator(), entries.subList(0, idx + 1));
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		int idx = inclusive ? ceilingIndex(fromKey) : higherIndex(fromKey);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		int size = size();
		return create(comparator(), entries.subList(idx, size));
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		entries.forEach(e -> action.accept(e.getKey(), e.getValue()));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, entries, (o, e) -> {
			o.writeObject(e.getKey());
			o.writeObject(e.getValue());
		});
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		entries = SerialUtils.readExternalImmutableList(in, i -> {
			@SuppressWarnings("unchecked")
			K key = (K) i.readObject();
			@SuppressWarnings("unchecked")
			V value = (V) i.readObject();
			return ImmutableUtils.makeImmutableMapEntry(key, value);
		});

	}
}
