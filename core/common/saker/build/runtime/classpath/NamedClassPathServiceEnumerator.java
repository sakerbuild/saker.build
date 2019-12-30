package saker.build.runtime.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import saker.apiextract.api.PublicApi;

/**
 * {@link ClassPathServiceEnumerator} implementation for locating a service based on a single class name.
 * <p>
 * The class will use {@link Class#forName(String, boolean, ClassLoader)} to get the class with the specified name.
 * <p>
 * This class doesn't do any type checking to ensure that the resulting class is actually is an instance of the expected
 * service type. To ensure that, use {@link NamedCheckingClassPathServiceEnumerator}.
 * 
 * @param <T>
 *            The type of the requested service.
 */
@PublicApi
public class NamedClassPathServiceEnumerator<T> implements ClassPathServiceEnumerator<T>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String className;

	/**
	 * For {@link Externalizable}.
	 */
	public NamedClassPathServiceEnumerator() {
	}

	/**
	 * Creates a new enumerator with the given class name.
	 * 
	 * @param className
	 *            The class name of the service to look up.
	 */
	public NamedClassPathServiceEnumerator(String className) {
		this.className = className;
	}

	@Override
	public Iterable<? extends T> getServices(ClassLoader classloader) throws ClassPathEnumerationError {
		try {
			return createIterable(Class.forName(className, false, classloader));
		} catch (ClassNotFoundException e) {
			throw new ClassPathEnumerationError("Class not found with name: " + className + " in " + classloader, e);
		}
	}

	/**
	 * Creates a lazily instantiating {@link Iterable} for the found class.
	 * 
	 * @param found
	 *            The found class.
	 * @return The created iterable.
	 */
	protected Iterable<? extends T> createIterable(Class<?> found) {
		return () -> new CreatingIterator<>(found);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(className);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		className = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((className == null) ? 0 : className.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NamedClassPathServiceEnumerator<?> other = (NamedClassPathServiceEnumerator<?>) obj;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + className + "]";
	}

	private static final class CreatingIterator<T> implements Iterator<T> {
		private Class<?> c;

		private CreatingIterator(Class<?> clazz) {
			Objects.requireNonNull(clazz);
			this.c = clazz;
		}

		@Override
		public boolean hasNext() {
			return c != null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public T next() {
			Class<?> c = this.c;
			if (c == null) {
				throw new NoSuchElementException();
			}
			this.c = null;
			try {
				return (T) c.getConstructor().newInstance();
			} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException e) {
				throw new ClassPathEnumerationError("Failed to instantiate class: " + c, e);
			}
		}
	}
}
