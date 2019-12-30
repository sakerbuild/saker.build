package saker.build.util.data.collection;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.util.data.collection.AdaptingIterable.AdaptingIterator;

public class AdaptingCollection<CollType extends Collection<?>> extends AbstractCollection<Object> {
	protected final ClassLoader cl;
	protected final CollType coll;

	public AdaptingCollection(ClassLoader cl, CollType iterable) {
		this.cl = cl;
		this.coll = iterable;
	}

	@Override
	public Iterator<Object> iterator() {
		return new AdaptingIterator(cl, coll);
	}

	@Override
	public int size() {
		return coll.size();
	}

	@Override
	public boolean isEmpty() {
		return coll.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		if (o == null) {
			for (Object e : coll) {
				if (e == null) {
					return true;
				}
			}
		} else {
			for (Object thiso : this) {
				if (o.equals(thiso)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean add(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}
}
