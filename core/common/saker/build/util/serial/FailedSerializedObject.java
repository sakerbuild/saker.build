package saker.build.util.serial;

import java.io.IOException;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ObjectUtils;

class FailedSerializedObject<E> implements SerializedObject<E> {
	private Supplier<? extends Exception> exceptionSupplier;

	public FailedSerializedObject(Supplier<? extends Exception> exceptionSupplier) {
		this.exceptionSupplier = exceptionSupplier;
	}

	@Override
	public E get() throws IOException, ClassNotFoundException {
		throw ObjectUtils.sneakyThrow(exceptionSupplier.get());
	}
}