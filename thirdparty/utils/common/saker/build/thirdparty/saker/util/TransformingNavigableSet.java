package saker.build.thirdparty.saker.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;

/**
 * Pseudo-{@link Set} implementation that dynamically generated the elements for it based on the elements of a subject
 * set.
 * <p>
 * Works exactly the same way as {@link TransformingSortedSet}, but also implements {@link NavigableSet} as well.
 * 
 * @param <SE>
 *            The source set element type.
 * @param <E>
 *            The element type of this set.
 */
public abstract class TransformingNavigableSet<SE, E> extends TransformingSortedSet<SE, E> implements NavigableSet<E> {

	/**
	 * Creates a new instance with the given set.
	 * 
	 * @param set
	 *            The subject set.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public TransformingNavigableSet(NavigableSet<? extends SE> set) throws NullPointerException {
		super(set);
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
	public TransformingNavigableSet(NavigableSet<? extends SE> set, Comparator<? super E> comparator)
			throws NullPointerException {
		super(set, comparator);
	}

	@Override
	public E lower(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E floor(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E ceiling(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E higher(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E pollFirst() {
		throw new UnsupportedOperationException();
	}

	@Override
	public E pollLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableSet<E> descendingSet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<E> descendingIterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		throw new UnsupportedOperationException();
	}

}
