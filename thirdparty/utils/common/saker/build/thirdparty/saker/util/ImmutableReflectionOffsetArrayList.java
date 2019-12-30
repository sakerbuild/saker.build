package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.util.Iterator;
import java.util.ListIterator;

@SuppressWarnings("rawtypes")
class ImmutableReflectionOffsetArrayList extends ReflectionOffsetArrayList {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableReflectionOffsetArrayList() {
	}

	public ImmutableReflectionOffsetArrayList(Object array, int offset, int length) {
		super(array, offset, length);
	}

	@Override
	public Object set(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator iterator() {
		return listIterator();
	}

	@Override
	public ListIterator listIterator() {
		return new ImmutableReflectionOffsetArrayListIterator<>(array, offset, length);
	}

	@Override
	public ListIterator listIterator(int index) {
		return new ImmutableReflectionOffsetArrayListIterator<>(array, offset, length, index);
	}
}
