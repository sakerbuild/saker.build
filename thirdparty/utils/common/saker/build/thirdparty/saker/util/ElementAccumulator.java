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

/**
 * Interface for simple container classes that allow elements to be added (accumulated) to it.
 * <p>
 * Implemetations of this interface allow elements of a given type to be added to them, and later iterated over them.
 * The actual nature of the additions, or other restrictions are implementation dependent.
 * <p>
 * This interface doesn't specify requirements regarding to thread safety, algorithmic complexity of the additions or
 * iteration order and behaviour.
 * <p>
 * Addition methods may throw implementation specific runtime exceptions if they cannot fulfil the request. E.g. no more
 * pre-allocated storage available, or requirements regarding the argument is violated.
 * 
 * @param <E>
 *            The element type.
 */
public interface ElementAccumulator<E> extends Iterable<E> {
	/**
	 * Adds an element to the accumulator.
	 * 
	 * @param element
	 *            The element to add.
	 */
	public void add(E element);

	/**
	 * Gets the current number of accumulated elements in this accumulator.
	 * 
	 * @return The number of elements present.
	 */
	public int size();
}
