package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.function.Function;

class FunctionTransformingIterator<T, E> extends TransformingIterator<T, E> {
	private Function<? super T, ? extends E> transformer;

	public FunctionTransformingIterator(Iterator<? extends T> it, Function<? super T, ? extends E> transformer) {
		super(it);
		this.transformer = transformer;
	}

	@Override
	protected E transform(T value) {
		return transformer.apply(value);
	}

}
