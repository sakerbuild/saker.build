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
import java.util.Iterator;

import saker.build.util.data.ConversionContext;
import saker.build.util.data.DataConverterUtils;

@SuppressWarnings("rawtypes")
public class ProxyIterator implements Iterator {
	protected final ConversionContext conversionContext;
	protected final Iterator it;
	protected final Type selfElementType;

	public ProxyIterator(ConversionContext conversionContext, Iterator it, Type selfElementType) {
		this.conversionContext = conversionContext;
		this.it = it;
		this.selfElementType = selfElementType;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public Object next() {
		return DataConverterUtils.convert(conversionContext, it.next(), selfElementType);
	}
}
