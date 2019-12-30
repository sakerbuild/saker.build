package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.util.List;
import java.util.RandomAccess;

class UnmodifiableRandomAccessList<E> extends UnmodifiableList<E> implements RandomAccess {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableRandomAccessList() {
	}

	public UnmodifiableRandomAccessList(List<? extends E> list) {
		super(list);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new UnmodifiableRandomAccessList<>(list.subList(fromIndex, toIndex));
	}
}
