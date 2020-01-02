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

import java.util.AbstractCollection;
import java.util.Iterator;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.DataConverterUtils;

public class AdaptingIterable extends AbstractCollection<Object> implements Iterable<Object> {
	public static final class AdaptingIterator implements Iterator<Object> {
		private final ClassLoader cl;
		private final Iterator<?> it;

		public AdaptingIterator(ClassLoader cl, Iterable<?> iterable) {
			this.cl = cl;
			this.it = iterable.iterator();
		}

		@Override
		public Object next() {
			return DataConverterUtils.adaptInterface(cl, it.next());
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}
	}

	protected final ClassLoader cl;
	protected final Iterable<?> iterable;

	public AdaptingIterable(ClassLoader cl, Iterable<?> iterable) {
		this.cl = cl;
		this.iterable = iterable;
	}

	@Override
	public Iterator<Object> iterator() {
		return new AdaptingIterator(cl, iterable);
	}

	@Override
	public int size() {
		return ObjectUtils.sizeOfIterable(iterable);
	}

}
