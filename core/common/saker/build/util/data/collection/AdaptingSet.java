package saker.build.util.data.collection;

import java.util.Collection;
import java.util.Set;

import saker.build.thirdparty.saker.util.ObjectUtils;

public class AdaptingSet extends AdaptingCollection<Set<?>> implements Set<Object> {
	public AdaptingSet(ClassLoader cl, Set<?> iterable) {
		super(cl, iterable);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.setHash(this);
	}

	@Override
	public boolean equals(Object o) {
		// based on AbstractSet source
		if (o == this)
			return true;

		if (!(o instanceof Set))
			return false;
		Collection<?> c = (Collection<?>) o;
		if (c.size() != size())
			return false;
		return containsAll(c);
	}
}
