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

import java.util.List;
import java.util.ListIterator;

class ImmutableReverseIterator<T> implements ListIterator<T> {
	protected ListIterator<? extends T> it;

	public ImmutableReverseIterator(ListIterator<? extends T> it) {
		this.it = it;
	}

	public ImmutableReverseIterator(List<? extends T> list) {
		this.it = list.listIterator(list.size());
	}

	@Override
	public boolean hasNext() {
		return it.hasPrevious();
	}

	@Override
	public T next() {
		return it.previous();
	}

	@Override
	public boolean hasPrevious() {
		return it.hasNext();
	}

	@Override
	public T previous() {
		return it.next();
	}

	@Override
	public int nextIndex() {
		return it.previousIndex();
	}

	@Override
	public int previousIndex() {
		return it.nextIndex();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(T e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException();
	}

}
