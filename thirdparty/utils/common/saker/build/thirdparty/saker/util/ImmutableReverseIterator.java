package saker.build.thirdparty.saker.util;

import java.util.List;
import java.util.ListIterator;

class ImmutableReverseIterator<T> implements ListIterator<T> {
	protected ListIterator<? extends T> it;

	public ImmutableReverseIterator(ListIterator<? extends T> it) {
		this.it = it;
	}

	public ImmutableReverseIterator(List<? extends T> list) {
		this.it = list.listIterator(list.size());
	}

	@Override
	public boolean hasNext() {
		return it.hasPrevious();
	}

	@Override
	public T next() {
		return it.previous();
	}

	@Override
	public boolean hasPrevious() {
		return it.hasNext();
	}

	@Override
	public T previous() {
		return it.next();
	}

	@Override
	public int nextIndex() {
		return it.previousIndex();
	}

	@Override
	public int previousIndex() {
		return it.nextIndex();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(T e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException();
	}

}
