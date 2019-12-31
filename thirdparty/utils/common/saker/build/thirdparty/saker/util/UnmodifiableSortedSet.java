package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

class UnmodifiableSortedSet<E> implements SortedSet<E>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SortedSet<E> set;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableSortedSet() {
	}

	public UnmodifiableSortedSet(SortedSet<E> set) {
		this.set = set;
	}

	@Override
	public Comparator<? super E> comparator() {
		return set.comparator();
	}

	@Override
	public E first() {
		return set.first();
	}

	@Override
	public E last() {
		return set.last();
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return set.contains(o);
	}

	@Override
	public Object[] toArray() {
		return set.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return set.toArray(a);
	}

	@Override
	public boolean add(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return set.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<E> iterator() {
		return new UnmodifiableIterator<>(set.iterator());
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		return new UnmodifiableSortedSet<>(set.subSet(fromElement, toElement));
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		return new UnmodifiableSortedSet<>(set.headSet(toElement));
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		return new UnmodifiableSortedSet<>(set.tailSet(fromElement));
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		set.forEach(action);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(set);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		set = (SortedSet<E>) in.readObject();
	}

	@Override
	public int hashCode() {
		return set.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return set.equals(obj);
	}

	@Override
	public String toString() {
		return set.toString();
	}

}
