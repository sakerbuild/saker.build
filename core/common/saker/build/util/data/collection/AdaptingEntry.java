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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import saker.build.util.data.DataConverterUtils;

public class AdaptingEntry implements Map.Entry<Object, Object> {
	protected final ClassLoader cl;
	protected final Entry<?, ?> entry;

	public AdaptingEntry(ClassLoader cl, Entry<?, ?> entry) {
		this.cl = cl;
		this.entry = entry;
	}

	@Override
	public Object getKey() {
		return DataConverterUtils.adaptInterface(cl, entry.getKey());
	}

	@Override
	public Object getValue() {
		return DataConverterUtils.adaptInterface(cl, entry.getValue());
	}

	@Override
	public Object setValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		Object k = getKey();
		Object v = getValue();
		return (k == null ? 0 : k.hashCode()) ^ (v == null ? 0 : v.hashCode());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Map.Entry)) {
			return false;
		}
		Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
		return Objects.equals(getKey(), e.getKey()) && Objects.equals(getValue(), e.getValue());
	}

	@Override
	public String toString() {
		return getKey() + "=" + getValue();
	}
}
