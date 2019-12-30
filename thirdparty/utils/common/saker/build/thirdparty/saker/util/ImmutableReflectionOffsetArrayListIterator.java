package saker.build.thirdparty.saker.util;

import java.lang.reflect.Array;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

class ImmutableReflectionOffsetArrayListIterator<T> implements ListIterator<T> {

	protected final Object array;
	protected final int offset;
	protected final int length;

	protected int index;

	public ImmutableReflectionOffsetArrayListIterator(Object array, int offset, int length) {
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	public ImmutableReflectionOffsetArrayListIterator(Object array, int offset, int length, int index) {
		this.array = array;
		this.offset = offset;
		this.length = length;
		this.index = index;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		Objects.requireNonNull(action, "action");
		while (index < length) {
			action.accept((T) Array.get(array, this.offset + index++));
		}
	}

	@Override
	public boolean hasNext() {
		return index < length;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		return (T) Array.get(array, this.offset + index++);
	}

	@Override
	public boolean hasPrevious() {
		return index > 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T previous() {
		if (!hasPrevious()) {
			throw new NoSuchElementException();
		}
		return (T) Array.get(array, this.offset + --index);
	}

	@Override
	public int nextIndex() {
		return index;
	}

	@Override
	public int previousIndex() {
		return index - 1;
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
