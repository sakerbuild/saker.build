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
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

class UnmodifiableMapEntrySet<K, V> implements Set<Map.Entry<K, V>>, Externalizable {
	private static final long serialVersionUID = 1L;
	private Set<? extends Map.Entry<? extends K, ? extends V>> set;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableMapEntrySet() {
	}

	public UnmodifiableMapEntrySet(Set<? extends Entry<? extends K, ? extends V>> set) {
		this.set = set;
	}

	@Override
	public Iterator<Entry<K, V>> iterator() {
		return ImmutableUtils.unmodifiableMapEntryIterator(set.iterator());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object[] toArray() {
		Object[] result = set.toArray();
		for (int i = 0; i < result.length; i++) {
			result[i] = ImmutableUtils.unmodifiableMapEntry((Entry<K, V>) result[i]);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] toArray(T[] a) {
		//XXX should wer call toArray on the set with a 0 length array, and then manipulate that?
		//do not pass the argument array directly to the super toArray, as multithreaded user can compromise the values
		T[] thisa = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length);
		Arrays.fill(thisa, UnmodifiableMapEntrySet.class);
		T[] result = set.toArray(thisa);
		if (result == thisa) {
			//the result array fits into the argument
			for (int i = 0; i < thisa.length; i++) {
				T r = thisa[i];
				if (r == UnmodifiableMapEntrySet.class) {
					//the entries after this should not be copied into the result array
					//so we return the argument array here
					return a;
				}
				//the element at the index i should be overwritten with an unmodifiable entry
				if (r == null) {
					a[i] = null;
					continue;
				}
				a[i] = (T) ImmutableUtils.unmodifiableMapEntry((Entry<K, V>) r);
			}
			return a;
		}
		//the super allocated a new array
		for (int i = 0; i < result.length; i++) {
			T r = result[i];
			result[i] = (T) ImmutableUtils.unmodifiableMapEntry((Entry<K, V>) r);
		}
		return result;
	}

	@Override
	public void forEach(Consumer<? super Entry<K, V>> action) {
		set.forEach(e -> action.accept(ImmutableUtils.unmodifiableMapEntry(e)));
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return set.contains(o);
	}

	@Override
	public boolean add(Map.Entry<K, V> e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return set.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends Map.Entry<K, V>> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(set);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		set = (Set<Map.Entry<K, ? extends V>>) in.readObject();
	}

	@Override
	public boolean removeIf(Predicate<? super Map.Entry<K, V>> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		return set.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return set.equals(obj);
	}

	@Override
	public String toString() {
		return set.toString();
	}
}
