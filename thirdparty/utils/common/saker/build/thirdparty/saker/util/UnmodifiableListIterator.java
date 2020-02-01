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

import java.util.ListIterator;
import java.util.function.Consumer;

class UnmodifiableListIterator<T> implements ListIterator<T> {
	private ListIterator<? extends T> it;

	public UnmodifiableListIterator(ListIterator<? extends T> it) {
		this.it = it;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public T next() {
		return it.next();
	}

	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		it.forEachRemaining(action);
	}

	@Override
	public boolean hasPrevious() {
		return it.hasPrevious();
	}

	@Override
	public T previous() {
		return it.previous();
	}

	@Override
	public int nextIndex() {
		return it.nextIndex();
	}

	@Override
	public int previousIndex() {
		return it.previousIndex();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove");
	}

	@Override
	public void set(T e) {
		throw new UnsupportedOperationException("set");
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException("add");
	}
}
