package saker.build.thirdparty.saker.util;

import java.lang.reflect.Array;

class ReflectionArrayListIterator<T> extends ImmutableReflectionArrayListIterator<T> {
	protected int lastIndex = -1;

	public ReflectionArrayListIterator(Object array, int index) {
		super(array, index);
	}

	public ReflectionArrayListIterator(Object array) {
		super(array);
	}

	@Override
	public T next() {
		T result = super.next();
		lastIndex = index;
		return result;
	}

	@Override
	public T previous() {
		T result = super.previous();
		lastIndex = index;
		return result;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(T e) {
		if (lastIndex < 0) {
			throw new IllegalStateException("next() or previous() has not yet been called.");
		}
		Array.set(array, lastIndex, e);
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException();
	}

}
