package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;

class EmptySortedSet<E> implements SortedSet<E>, Externalizable {
	private static final long serialVersionUID = 1L;

	static final SortedSet<?> EMPTY_SORTED_SET = new EmptySortedSet<>();

	public EmptySortedSet() {
	}

	@Override
	public Comparator<? super E> comparator() {
		return null;
	}

	@Override
	public final E first() {
		throw new NoSuchElementException();
	}

	@Override
	public final E last() {
		throw new NoSuchElementException();
	}

	@Override
	public final int size() {
		return 0;
	}

	@Override
	public final boolean isEmpty() {
		return true;
	}

	@Override
	public final boolean contains(Object o) {
		return false;
	}

	@Override
	public final Object[] toArray() {
		return ObjectUtils.EMPTY_OBJECT_ARRAY;
	}

	@Override
	public final <T> T[] toArray(T[] a) {
		if (a.length == 0) {
			return a;
		}
		a[0] = null;
		return a;
	}

	@Override
	public final boolean add(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean containsAll(Collection<?> c) {
		return false;
	}

	@Override
	public final boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final Iterator<E> iterator() {
		return Collections.emptyIterator();
	}

	@Override
	public final SortedSet<E> subSet(E fromElement, E toElement) {
		return this;
	}

	@Override
	public final SortedSet<E> headSet(E toElement) {
		return this;
	}

	@Override
	public final SortedSet<E> tailSet(E fromElement) {
		return this;
	}

	@Override
	public final String toString() {
		return "[]";
	}

	@Override
	public final int hashCode() {
		return 0;
	}

	@Override
	public final boolean equals(Object obj) {
		if (obj == this)
			return true;

		if (!(obj instanceof Set))
			return false;
		return ((Set<?>) obj).isEmpty();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	private Object readResolve() {
		return EMPTY_SORTED_SET;
	}
}
