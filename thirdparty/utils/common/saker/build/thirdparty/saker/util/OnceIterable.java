package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class OnceIterable<T> implements Iterable<T> {
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<OnceIterable, Iterator> ARFU_iterator = AtomicReferenceFieldUpdater
			.newUpdater(OnceIterable.class, Iterator.class, "iterator");

	@SuppressWarnings("unused")
	private volatile Iterator<T> iterator;

	public OnceIterable(Iterator<T> iterator) {
		this.iterator = iterator;
	}

	@Override
	public Iterator<T> iterator() {
		@SuppressWarnings("unchecked")
		Iterator<T> got = ARFU_iterator.getAndSet(this, null);
		if (got == null) {
			throw new IllegalStateException("Already returned iterator.");
		}
		return got;
	}

}
