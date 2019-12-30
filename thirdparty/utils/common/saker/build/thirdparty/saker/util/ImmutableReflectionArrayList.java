package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.util.Iterator;
import java.util.ListIterator;

@SuppressWarnings("rawtypes")
class ImmutableReflectionArrayList extends ReflectionArrayList {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableReflectionArrayList() {
	}

	public ImmutableReflectionArrayList(Object array) {
		super(array);
	}

	public ImmutableReflectionArrayList(Object array, int length) {
		super(array, length);
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
		return new ImmutableReflectionArrayListIterator<>(array);
	}

	@Override
	public ListIterator listIterator(int index) {
		return new ImmutableReflectionArrayListIterator<>(array, index);
	}
}
