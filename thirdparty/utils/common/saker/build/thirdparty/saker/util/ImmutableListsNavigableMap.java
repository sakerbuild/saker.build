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
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.function.BiConsumer;

class ImmutableListsNavigableMap<K, V> extends ImmutableNavigableMapBase<K, V> implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected List<K> keys;
	protected List<V> values;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableListsNavigableMap() {
	}

	protected ImmutableListsNavigableMap(List<K> keys, List<V> values) {
		this.keys = keys;
		this.values = values;
	}

	//doc: responsibility of the caller to provide valid parameters
	public static <K, V> NavigableMap<K, V> create(Comparator<? super K> comparator, List<K> keys, List<V> values) {
		if (comparator == null) {
			return new ImmutableListsNavigableMap<>(keys, values);
		}
		return new ImmutableListsComparatorNavigableMap<>(keys, values, comparator);
	}

	@Override
	public Comparator<? super K> comparator() {
		return null;
	}

	@Override
	protected final int binarySearch(K k) {
		return Collections.binarySearch(keys, k, comparator());
	}

	@Override
	protected final Entry<K, V> entryAt(int idx) {
		return ImmutableUtils.makeImmutableMapEntry(keys.get(idx), values.get(idx));
	}

	@Override
	protected K keyAt(int idx) {
		return keys.get(idx);
	}

	@Override
	protected V valueAt(int idx) {
		return values.get(idx);
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		return new ImmutableListsNavigableMapDescendingView<>(this);
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		return ImmutableListNavigableSet.create(comparator(), keys);
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
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
		return create(comparator(), keys.subList(fromidx, toidx + 1), values.subList(fromidx, toidx + 1));
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		int idx = inclusive ? floorIndex(toKey) : lowerIndex(toKey);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		return create(comparator(), keys.subList(0, idx + 1), values.subList(0, idx + 1));
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		int idx = inclusive ? ceilingIndex(fromKey) : higherIndex(fromKey);
		if (idx < 0) {
			return ImmutableUtils.emptyNavigableMap(comparator());
		}
		int size = size();
		return create(comparator(), keys.subList(idx, size), values.subList(idx, size));
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new EntrySet();
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		for (int i = 0, s = keys.size(); i < s; i++) {
			K k = keys.get(i);
			V v = values.get(i);
			action.accept(k, v);
		}
	}

	@Override
	public Collection<V> values() {
		return values;
	}

	@Override
	public int size() {
		return keys.size();
	}

	@Override
	public boolean isEmpty() {
		return keys.isEmpty();
	}

	@Override
	public boolean containsValue(Object value) {
		return values.contains(value);
	}

	private static final class ImmutableListsNavigableMapDescendingView<K, V> extends ReverseNavigableMapView<K, V> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public ImmutableListsNavigableMapDescendingView() {
		}

		private ImmutableListsNavigableMapDescendingView(ImmutableListsNavigableMap<K, V> map) {
			super(map);
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			return ((ImmutableListsNavigableMap<K, V>) map).new ReverseEntrySet();
		}

		@Override
		public Collection<V> values() {
			return ObjectUtils.reversedList(((ImmutableListsNavigableMap<K, V>) map).values);
		}
	}

	private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
		@Override
		public Iterator<Entry<K, V>> iterator() {
			Iterator<? extends K> kit = keys.iterator();
			Iterator<? extends V> vit = values.iterator();
			return new Iterator<Map.Entry<K, V>>() {

				@Override
				public boolean hasNext() {
					return kit.hasNext();
				}

				@Override
				public Entry<K, V> next() {
					return ImmutableUtils.makeImmutableMapEntry(kit.next(), vit.next());
				}
			};
		}

		@Override
		public int size() {
			return keys.size();
		}
	}

	private final class ReverseEntrySet extends AbstractSet<Map.Entry<K, V>> {
		@Override
		public Iterator<Entry<K, V>> iterator() {
			int size = size();
			ListIterator<? extends K> kit = keys.listIterator(size);
			ListIterator<? extends V> vit = values.listIterator(size);
			return new Iterator<Map.Entry<K, V>>() {

				@Override
				public boolean hasNext() {
					return kit.hasPrevious();
				}

				@Override
				public Entry<K, V> next() {
					return ImmutableUtils.makeImmutableMapEntry(kit.previous(), vit.previous());
				}
			};
		}

		@Override
		public int size() {
			return keys.size();
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(keys);
		out.writeObject(values);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		keys = (List<K>) in.readObject();
		values = (List<V>) in.readObject();
	}

}
