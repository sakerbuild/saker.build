package saker.build.util.serial;

class PresentSerializedObject<T> implements SerializedObject<T> {
	private T object;

	public PresentSerializedObject(T object) {
		this.object = object;
	}

	@Override
	public T get() {
		return object;
	}
}