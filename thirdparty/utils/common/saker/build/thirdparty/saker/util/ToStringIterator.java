package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.Objects;

final class ToStringIterator implements Iterator<String> {
	private final Iterator<?> it;

	public ToStringIterator(Iterator<?> it) {
		this.it = it;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public String next() {
		return Objects.toString(it.next());
	}

	@Override
	public void remove() {
		it.remove();
	}
}