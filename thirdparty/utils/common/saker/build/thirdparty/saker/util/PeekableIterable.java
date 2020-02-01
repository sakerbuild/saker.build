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

import java.util.Iterator;

/**
 * {@link Iterable} subinterface that supports peeking the first element, and checking if it is empty.
 * <p>
 * The purpose of this interface is to be able to query some information without the need of creating a new
 * {@link Iterator}.
 * 
 * @param <T>
 *            The element type.
 */
public interface PeekableIterable<T> extends Iterable<T> {
	/**
	 * Gets the first element in this iterable, or <code>null</code> if there's none.
	 * <p>
	 * If the first element is <code>null</code>, this method returns the same nonetheless. Clients are recommended to
	 * check {@link #isEmpty()} if the iterable may contain <code>null</code>s.
	 * 
	 * @return The first element or <code>null</code>, if there's none.
	 */
	public T peek();

	/**
	 * Checks if the iterable is empty.
	 * <p>
	 * If it is empty, {@link #peek()} will return <code>null</code>, and any iterators created will have no next
	 * elements.
	 * 
	 * @return <code>true</code> if the iterable contains no elements.
	 */
	public boolean isEmpty();
}