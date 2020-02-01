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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

class UnmodifiableMapEntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
	private Iterator<? extends Map.Entry<? extends K, ? extends V>> it;

	public UnmodifiableMapEntryIterator(Iterator<? extends Map.Entry<? extends K, ? extends V>> it) {
		this.it = it;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public Map.Entry<K, V> next() {
		Entry<? extends K, ? extends V> n = it.next();
		return ImmutableUtils.unmodifiableMapEntry(n);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove");
	}

	@Override
	public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
		it.forEachRemaining(e -> ImmutableUtils.unmodifiableMapEntry(e));
	}
}
