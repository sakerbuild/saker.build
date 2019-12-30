package saker.build.thirdparty.saker.util;

import java.util.Iterator;

abstract class TransformingIterator<T, E> implements Iterator<E> {
	protected Iterator<? extends T> it;

	public TransformingIterator(Iterator<? extends T> it) {
		this.it = it;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public E next() {
		return transform(it.next());
	}

	protected abstract E transform(T value);
}
