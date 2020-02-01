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
import java.util.Iterator;
import java.util.ListIterator;

@SuppressWarnings("rawtypes")
class ImmutableReflectionArrayList extends ReflectionArrayList {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableReflectionArrayList() {
	}

	public ImmutableReflectionArrayList(Object array) {
		super(array);
	}

	public ImmutableReflectionArrayList(Object array, int length) {
		super(array, length);
	}

	@Override
	public Object set(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator iterator() {
		return listIterator();
	}

	@Override
	public ListIterator listIterator() {
		return new ImmutableReflectionArrayListIterator<>(array);
	}

	@Override
	public ListIterator listIterator(int index) {
		return new ImmutableReflectionArrayListIterator<>(array, index);
	}
}
