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
 * {@link Iterable} class that provides an iterator that concatenates multiple iterables.
 * <p>
 * This class takes an iterable of iterables during its construction, and will provide an iterator that iterates over
 * them after each other.
 * <p>
 * The iterator supports removals only if the iterator that returned the last element supports it.
 * 
 * @param <T>
 *            The element type.
 */
public final class ConcatIterable<T> implements Iterable<T> {
	private static final class Itr<T> implements Iterator<T> {
		private final Iterator<? extends Iterable<? extends T>> iterableIt;
		private Iterator<? extends T> currentIt;
		private Iterator<? extends T> lastIt;

		private Itr(Iterator<? extends Iterable<? extends T>> iterableIt) {
			this.iterableIt = iterableIt;
			this.currentIt = Collections.emptyIterator();
			this.lastIt = Collections.emptyIterator();
			moveToNext();
		}

		private void moveToNext() {
			while (!currentIt.hasNext()) {
				if (!iterableIt.hasNext()) {
					//no next
					break;
				}
				currentIt = iterableIt.next().iterator();
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

	private Iterable<? extends Iterable<? extends T>> iterables;

	/**
	 * Creates a new instance.
	 * 
	 * @param iterables
	 *            The iterable of iterables that should be iterated over.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcatIterable(Iterable<? extends Iterable<? extends T>> iterables) throws NullPointerException {
		Objects.requireNonNull(iterables, "iterables");
		this.iterables = iterables;
	}

	@Override
	public Iterator<T> iterator() {
		return new Itr<>(iterables.iterator());
	}

	@Override
	public String toString() {
		return ObjectUtils.collectionToString(this);
	}
}
