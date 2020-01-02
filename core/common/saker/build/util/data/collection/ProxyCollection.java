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
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.util.data.ConversionContext;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProxyCollection<CollType extends Collection> extends AbstractCollection {
	protected final ConversionContext conversionContext;
	protected final CollType coll;
	protected transient final Type selfElementType;

	public ProxyCollection(ConversionContext conversionContext, CollType coll, Type selfElementType) {
		this.conversionContext = conversionContext;
		this.coll = coll;
		this.selfElementType = selfElementType;
	}

	@Override
	public int size() {
		return coll.size();
	}

	@Override
	public boolean isEmpty() {
		return coll.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		for (Object thiso : this) {
			if (Objects.equals(thiso, o)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator iterator() {
		Iterator it = coll.iterator();
		return new ProxyIterator(conversionContext, it, selfElementType);
	}

	@Override
	public boolean add(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}

	//not implementing hashCode and equals as Collection interface doesnt demand it
}
