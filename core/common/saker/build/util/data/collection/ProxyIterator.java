package saker.build.util.data.collection;

import java.lang.reflect.Type;
import java.util.Iterator;

import saker.build.util.data.ConversionContext;
import saker.build.util.data.DataConverterUtils;

@SuppressWarnings("rawtypes")
public class ProxyIterator implements Iterator {
	protected final ConversionContext conversionContext;
	protected final Iterator it;
	protected final Type selfElementType;

	public ProxyIterator(ConversionContext conversionContext, Iterator it, Type selfElementType) {
		this.conversionContext = conversionContext;
		this.it = it;
		this.selfElementType = selfElementType;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public Object next() {
		return DataConverterUtils.convert(conversionContext, it.next(), selfElementType);
	}
}
