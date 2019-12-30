package saker.build.util.serial;

import java.io.IOException;

interface SerializedObject<T> {
	public T get() throws IOException, ClassNotFoundException;
}
