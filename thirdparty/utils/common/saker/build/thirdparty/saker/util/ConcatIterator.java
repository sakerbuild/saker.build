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

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/**
 * {@link Iterator} that concatenates other iterators.
 * <p>
 * This class takes an iterator of iterators during its construction, and will iterate over them after each other.
 * <p>
 * The iterator supports removals only if the iterator that returned the last element supports it.
 * 
 * @param <T>
 *            The element type.
 */
public final class ConcatIterator<T> implements Iterator<T> {
	private Iterator<? extends Iterator<? extends T>> iterators;
	private Iterator<? extends T> currentIt;
	private Iterator<? extends T> lastIt;

	/**
	 * Creates a new instance.
	 * 
	 * @param iterators
	 *            The iterator of iterators that should be iterated over.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcatIterator(Iterator<? extends Iterator<? extends T>> iterators) throws NullPointerException {
		Objects.requireNonNull(iterators, "iterators");
		this.iterators = iterators;
		this.currentIt = Collections.emptyIterator();
		this.lastIt = Collections.emptyIterator();
		moveToNext();
	}

	private void moveToNext() {
		while (!currentIt.hasNext()) {
			if (!iterators.hasNext()) {
				break;
			}
			currentIt = iterators.next();
		}
	}

	@Override
	public boolean hasNext() {
		return currentIt.hasNext();
	}

	@Override
	public T next() {
		T result = currentIt.next();
		lastIt = currentIt;
		moveToNext();
		return result;
	}

	@Override
	public void remove() {
		lastIt.remove();
	}
}
