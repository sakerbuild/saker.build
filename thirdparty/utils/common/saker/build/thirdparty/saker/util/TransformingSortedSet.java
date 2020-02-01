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

import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Pseudo-{@link Set} implementation that dynamically generated the elements for it based on the elements of a subject
 * set.
 * <p>
 * This set works the same way as {@link TransformingSet}, but also implements {@link SortedSet}.
 * <p>
 * A comparator can be specified during the construction of this set, which will be reported when {@link #comparator()}
 * is called. When this set is used as the argument to a constructor of a sorted set (E.g.
 * {@link TreeSet#TreeSet(SortedSet)}), this will cause the constructed set to have the same ordering as this set.
 * <p>
 * <b>Important:</b> Implementations should ensure that the transformed elements are ordered by the comparator of this
 * constructed set. Violating this may result in undefined behaviour in some implementations.
 * <p>
 * The use-case for this map is the same as for {@link TransformingSet}. See the documentation of that class for more
 * information.
 * 
 * @param <SE>
 *            The source set element type.
 * @param <E>
 *            The element type of this set.
 */
public abstract class TransformingSortedSet<SE, E> extends TransformingSet<SE, E> implements SortedSet<E> {
	/**
	 * The comparator that this set is ordered by.
	 */
	protected final Comparator<? super E> comparator;

	/**
	 * Creates a new instance with the given set.
	 * 
	 * @param set
	 *            The subject set.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public TransformingSortedSet(SortedSet<? extends SE> set) throws NullPointerException {
		this(set, null);
	}

	/**
	 * Creates a new instance with the given set and comparator.
	 * 
	 * @param set
	 *            The subject set.
	 * @param comparator
	 *            The comparator.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public TransformingSortedSet(SortedSet<? extends SE> set, Comparator<? super E> comparator)
			throws NullPointerException {
		super(set);
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super E> comparator() {
		return comparator;
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E first() {
		throw new UnsupportedOperationException();
	}

	@Override
	public E last() {
		throw new UnsupportedOperationException();
	}

}
