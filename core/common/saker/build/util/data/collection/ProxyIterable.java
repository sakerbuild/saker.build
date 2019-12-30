package saker.build.util.data.collection;

import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.Iterator;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.ConversionContext;

@SuppressWarnings("rawtypes")
public class ProxyIterable extends AbstractCollection implements Iterable {
	protected final ConversionContext conversionContext;
	protected final Iterable<?> iterable;
	protected transient final Type selfElementType;

	public ProxyIterable(ConversionContext conversionContext, Iterable<?> iterable, Type selfElementType) {
		this.conversionContext = conversionContext;
		this.iterable = iterable;
		this.selfElementType = selfElementType;
	}

	@Override
	public Iterator<?> iterator() {
		return new ProxyIterator(conversionContext, iterable.iterator(), selfElementType);
	}

	@Override
	public int size() {
		return ObjectUtils.sizeOfIterable(iterable);
	}
}
