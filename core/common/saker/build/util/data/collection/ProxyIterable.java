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
import java.util.Iterator;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.ConversionContext;

@SuppressWarnings("rawtypes")
public class ProxyIterable extends AbstractCollection implements Iterable {
	protected final ConversionContext conversionContext;
	protected final Iterable<?> iterable;
	protected transient final Type selfElementType;

	public ProxyIterable(ConversionContext conversionContext, Iterable<?> iterable, Type selfElementType) {
		this.conversionContext = conversionContext;
		this.iterable = iterable;
		this.selfElementType = selfElementType;
	}

	@Override
	public Iterator<?> iterator() {
		return new ProxyIterator(conversionContext, iterable.iterator(), selfElementType);
	}

	@Override
	public int size() {
		return ObjectUtils.sizeOfIterable(iterable);
	}
}
