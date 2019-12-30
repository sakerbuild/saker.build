package saker.build.util.serial;

import java.io.IOException;

class UnavailableSerializedObject<T> implements SerializedObject<T> {
	public static final UnavailableSerializedObject<?> INSTANCE = new UnavailableSerializedObject<>();

	@SuppressWarnings("unchecked")
	public static <T> UnavailableSerializedObject<T> instance() {
		return (UnavailableSerializedObject<T>) INSTANCE;
	}

	@Override
	public T get() throws IOException, ClassNotFoundException {
		throw new SerializationReflectionException("Object is not yet accessible.");
	}
}
