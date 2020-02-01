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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

final class UnmodifiableMapEntry<K, V> implements Map.Entry<K, V>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Map.Entry<? extends K, ? extends V> entry;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableMapEntry() {
	}

	public UnmodifiableMapEntry(Entry<? extends K, ? extends V> entry) {
		Objects.requireNonNull(entry, "entry");
		this.entry = entry;
	}

	@Override
	public K getKey() {
		return entry.getKey();
	}

	@Override
	public V getValue() {
		return entry.getValue();
	}

	@Override
	public V setValue(V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Map.Entry))
			return false;
		Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
		return Objects.equals(getKey(), e.getKey()) && Objects.equals(getValue(), e.getValue());
	}

	@Override
	public int hashCode() {
		K key = getKey();
		V val = getValue();
		return (key == null ? 0 : key.hashCode()) ^ (val == null ? 0 : val.hashCode());
	}

	@Override
	public String toString() {
		return getKey() + "=" + getValue();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(entry);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		entry = (Entry<? extends K, ? extends V>) in.readObject();
	}
}
