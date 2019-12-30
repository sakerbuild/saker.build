package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

class UnmodifiableList<E> implements List<E>, Externalizable {
	private static final long serialVersionUID = 1L;

	protected List<? extends E> list;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableList() {
	}

	public UnmodifiableList(List<? extends E> list) {
		this.list = list;
	}

	@Override
	public final int size() {
		return list.size();
	}

	@Override
	public final boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public final boolean contains(Object o) {
		return list.contains(o);
	}

	@Override
	public final Iterator<E> iterator() {
		return new UnmodifiableIterator<>(list.iterator());
	}

	@Override
	public final Object[] toArray() {
		return list.toArray();
	}

	@Override
	public final <T> T[] toArray(T[] a) {
		return list.toArray(a);
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
		return list.containsAll(c);
	}

	@Override
	public final boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean addAll(int index, Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final E get(int index) {
		return list.get(index);
	}

	@Override
	public final E set(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void add(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final E remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final int indexOf(Object o) {
		return list.indexOf(o);
	}

	@Override
	public final int lastIndexOf(Object o) {
		return list.lastIndexOf(o);
	}

	@Override
	public final ListIterator<E> listIterator() {
		return new UnmodifiableListIterator<>(list.listIterator());
	}

	@Override
	public final ListIterator<E> listIterator(int index) {
		return new UnmodifiableListIterator<>(list.listIterator(index));
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new UnmodifiableList<>(list.subList(fromIndex, toIndex));
	}

	@Override
	public final boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void forEach(Consumer<? super E> action) {
		list.forEach(action);
	}

	@Override
	public final void replaceAll(UnaryOperator<E> operator) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void sort(Comparator<? super E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(list);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		list = (List<? extends E>) in.readObject();
	}

	@Override
	public final int hashCode() {
		return list.hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return list.equals(obj);
	}

	@Override
	public final String toString() {
		return list.toString();
	}

}
