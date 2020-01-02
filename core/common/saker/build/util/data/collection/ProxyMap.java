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
package saker.build.util.data.collection;

import java.lang.reflect.Type;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.ConversionContext;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProxyMap implements Map {

	protected final ConversionContext keyConversionContext;
	protected final ConversionContext valueConversionContext;
	protected final transient Map map;
	protected final transient Type selfKeyType;
	protected final transient Type selfValueType;

	public ProxyMap(ConversionContext keyConversionContext, ConversionContext valueConversionContext, Map map,
			Type selfKeyType, Type selfValueType) {
		this.keyConversionContext = keyConversionContext;
		this.valueConversionContext = valueConversionContext;
		this.map = map;
		this.selfKeyType = selfKeyType;
		this.selfValueType = selfValueType;
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
		// Object
		if (key == null) {
			for (Object k : keySet()) {
				if (k == null) {
					return true;
				}
			}
		} else {
			for (Object k : keySet()) {
				if (key.equals(k)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean containsValue(Object value) {
		// Object
		if (value == null) {
			for (Object v : values()) {
				if (v == null) {
					return true;
				}
			}
		} else {
			for (Object v : values()) {
				if (value.equals(v)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public Object get(Object key) {
		// V, Object
		if (key == null) {
			for (Entry entry : entrySet()) {
				if (entry.getKey() == null) {
					return entry.getValue();
				}
			}
		} else {
			for (Entry entry : entrySet()) {
				if (key.equals(entry.getKey())) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	@Override
	public Object put(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set keySet() {
		// Set<K>
		return new ProxySet(keyConversionContext, map.keySet(), selfKeyType);
	}

	@Override
	public Collection values() {
		// Collection<V>
		return new ProxyCollection(valueConversionContext, map.values(), selfValueType);
	}

	@Override
	public Set<Map.Entry> entrySet() {
		// Set<Map.Entry<K, V>>
		return new ProxyEntrySet(map.entrySet());
	}

	private class ProxyEntrySet extends AbstractSet {
		private Set set;

		public ProxyEntrySet(Set set) {
			this.set = set;
		}

		@Override
		public Iterator iterator() {
			Iterator it = set.iterator();
			return new Iterator() {
				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public Map.Entry next() {
					return new ProxyMapEntry(keyConversionContext, valueConversionContext, (Entry) it.next(),
							selfKeyType, selfValueType);
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};

		}

		@Override
		public int size() {
			return ProxyMap.this.size();
		}
	}

	@Override
	public String toString() {
		// based on AbstractMap source
		if (isEmpty()) {
			return "{}";
		}
		Iterator<Entry> i = entrySet().iterator();

		StringBuilder sb = new StringBuilder();
		sb.append('{');
		for (;;) {
			Entry e = i.next();
			Object key = e.getKey();
			Object value = e.getValue();
			sb.append(key == this ? "(this Map)" : key);
			sb.append('=');
			sb.append(value == this ? "(this Map)" : value);
			if (!i.hasNext())
				return sb.append('}').toString();
			sb.append(',').append(' ');
		}
	}

	@Override
	public int hashCode() {
		return ObjectUtils.mapHash(map);
	}

	@Override
	public boolean equals(Object o) {
		// based on AbstractMap source
		if (o == this)
			return true;
		if (!(o instanceof Map))
			return false;

		Map<?, ?> m = (Map<?, ?>) o;
		if (m.size() != size())
			return false;

		Iterator<Entry> i = entrySet().iterator();
		while (i.hasNext()) {
			Entry e = i.next();
			Object key = e.getKey();
			Object value = e.getValue();
			if (value == null) {
				if (!(m.get(key) == null && m.containsKey(key))) {
					return false;
				}
			} else {
				if (!value.equals(m.get(key))) {
					return false;
				}
			}
		}
		return true;
	}
}
