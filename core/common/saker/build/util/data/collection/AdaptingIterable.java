package saker.build.util.data.collection;

import java.util.AbstractCollection;
import java.util.Iterator;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.DataConverterUtils;

public class AdaptingIterable extends AbstractCollection<Object> implements Iterable<Object> {
	public static final class AdaptingIterator implements Iterator<Object> {
		private final ClassLoader cl;
		private final Iterator<?> it;

		public AdaptingIterator(ClassLoader cl, Iterable<?> iterable) {
			this.cl = cl;
			this.it = iterable.iterator();
		}

		@Override
		public Object next() {
			return DataConverterUtils.adaptInterface(cl, it.next());
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}
	}

	protected final ClassLoader cl;
	protected final Iterable<?> iterable;

	public AdaptingIterable(ClassLoader cl, Iterable<?> iterable) {
		this.cl = cl;
		this.iterable = iterable;
	}

	@Override
	public Iterator<Object> iterator() {
		return new AdaptingIterator(cl, iterable);
	}

	@Override
	public int size() {
		return ObjectUtils.sizeOfIterable(iterable);
	}

}
