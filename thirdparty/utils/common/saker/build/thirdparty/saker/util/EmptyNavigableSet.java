package saker.build.thirdparty.saker.util;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;

class EmptyNavigableSet<E> extends EmptySortedSet<E> implements NavigableSet<E> {
	private static final long serialVersionUID = 1L;

	static final NavigableSet<?> EMPTY_NAVIGABLE_SET = new EmptyNavigableSet<>();

	public EmptyNavigableSet() {
	}

	@Override
	public Comparator<? super E> comparator() {
		return null;
	}

	@Override
	public final E lower(E e) {
		return null;
	}

	@Override
	public final E floor(E e) {
		return null;
	}

	@Override
	public final E ceiling(E e) {
		return null;
	}

	@Override
	public final E higher(E e) {
		return null;
	}

	@Override
	public final E pollFirst() {
		return null;
	}

	@Override
	public final E pollLast() {
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final NavigableSet<E> descendingSet() {
		return ImmutableUtils.emptyNavigableSet(Collections.reverseOrder(comparator()));
	}

	@Override
	public final Iterator<E> descendingIterator() {
		return Collections.emptyIterator();
	}

	@Override
	public final NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return this;
	}

	@Override
	public final NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return this;
	}

	@Override
	public final NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	private Object readResolve() {
		return EMPTY_NAVIGABLE_SET;
	}
}
