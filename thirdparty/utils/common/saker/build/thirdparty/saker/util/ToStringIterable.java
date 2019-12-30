package saker.build.thirdparty.saker.util;

import java.util.Iterator;

class ToStringIterable implements Iterable<String> {
	private Iterable<?> iterable;

	public ToStringIterable(Iterable<?> iterable) {
		this.iterable = iterable;
	}

	@Override
	public Iterator<String> iterator() {
		return new ToStringIterator(iterable.iterator());
	}
}
