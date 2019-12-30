package saker.build.util.data.collection;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;
import java.util.Spliterator;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.ConversionContext;

@SuppressWarnings({ "rawtypes" })
public class ProxySet extends ProxyCollection<Set> implements Set {
	public ProxySet(ConversionContext conversionContext, Set coll, Type selfElementType) {
		super(conversionContext, coll, selfElementType);
	}

	@Override
	public Spliterator spliterator() {
		return Set.super.spliterator();
	}

	@Override
	public int hashCode() {
		return ObjectUtils.setHash(this);
	}

	@SuppressWarnings("unchecked")
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
