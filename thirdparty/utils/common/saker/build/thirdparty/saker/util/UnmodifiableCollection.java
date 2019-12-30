package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

class UnmodifiableCollection<E> implements Collection<E>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Collection<? extends E> coll;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableCollection() {
	}

	public UnmodifiableCollection(Collection<? extends E> coll) {
		this.coll = coll;
	}

	@Override
	public int size() {
		return coll.size();
	}

	@Override
	public boolean isEmpty() {
		return coll.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return coll.contains(o);
	}

	@Override
	public Iterator<E> iterator() {
		return new UnmodifiableIterator<>(coll.iterator());
	}

	@Override
	public Object[] toArray() {
		return coll.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return coll.toArray(a);
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
		return coll.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(coll);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		coll = (Collection<? extends E>) in.readObject();
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		coll.forEach(action);
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		return coll.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return coll.equals(obj);
	}

	@Override
	public String toString() {
		return coll.toString();
	}

}
