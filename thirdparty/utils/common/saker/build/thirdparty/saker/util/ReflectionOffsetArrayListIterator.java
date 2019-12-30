package saker.build.thirdparty.saker.util;

import java.lang.reflect.Array;

class ReflectionOffsetArrayListIterator<T> extends ImmutableReflectionOffsetArrayListIterator<T> {
	protected int lastIndex = -1;

	public ReflectionOffsetArrayListIterator(Object array, int offset, int length, int index) {
		super(array, offset, length, index);
	}

	public ReflectionOffsetArrayListIterator(Object array, int offset, int length) {
		super(array, offset, length);
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
		Array.set(array, this.offset + lastIndex, e);
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException();
	}

}
