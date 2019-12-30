package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.function.Consumer;

class UnmodifiableIterator<T> implements Iterator<T> {
	private Iterator<? extends T> it;

	public UnmodifiableIterator(Iterator<? extends T> it) {
		this.it = it;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public T next() {
		return it.next();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove");
	}

	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		it.forEachRemaining(action);
	}
}
