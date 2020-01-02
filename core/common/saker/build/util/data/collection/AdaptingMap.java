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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class AdaptingMap extends AbstractMap<Object, Object> {

	protected final ClassLoader cl;
	protected final Map<?, ?> map;

	public AdaptingMap(ClassLoader cl, Map<?, ?> iterable) {
		this.cl = cl;
		this.map = iterable;
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public void replaceAll(BiFunction<? super Object, ? super Object, ? extends Object> function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object putIfAbsent(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean replace(Object key, Object oldValue, Object newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object replace(Object key, Object value) {
		throw new UnsupportedOperationException();
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
	public void putAll(Map<? extends Object, ? extends Object> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Entry<Object, Object>> entrySet() {
		return new AbstractSet<Map.Entry<Object, Object>>() {
			private Set<? extends Entry<?, ?>> realEntrySet = map.entrySet();

			@Override
			public Iterator<Entry<Object, Object>> iterator() {
				return new Iterator<Map.Entry<Object, Object>>() {
					private Iterator<? extends Entry<?, ?>> it = realEntrySet.iterator();

					@Override
					public Entry<Object, Object> next() {
						Entry<?, ?> n = it.next();
						return new AdaptingEntry(cl, n);
					}

					@Override
					public boolean hasNext() {
						return it.hasNext();
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
