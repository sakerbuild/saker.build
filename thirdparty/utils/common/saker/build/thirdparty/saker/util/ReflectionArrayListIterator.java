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

import java.lang.reflect.Array;

class ReflectionArrayListIterator<T> extends ImmutableReflectionArrayListIterator<T> {
	protected int lastIndex = -1;

	public ReflectionArrayListIterator(Object array, int index) {
		super(array, index);
	}

	public ReflectionArrayListIterator(Object array) {
		super(array);
	}

	@Override
	public T next() {
		T result = super.next();
		lastIndex = index;
		return result;
	}

	@Override
	public T previous() {
		T result = super.previous();
		lastIndex = index;
		return result;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(T e) {
		if (lastIndex < 0) {
			throw new IllegalStateException("next() or previous() has not yet been called.");
		}
		Array.set(array, lastIndex, e);
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException();
	}

}
