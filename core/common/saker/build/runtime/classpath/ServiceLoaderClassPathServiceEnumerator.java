package saker.build.runtime.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import saker.apiextract.api.PublicApi;

/**
 * {@link ClassPathServiceEnumerator} implementation backed by the {@link ServiceLoader} lookup functionality.
 * <p>
 * This class uses {@link ServiceLoader} to enumerate the services.
 * <p>
 * The implementation will re-throw any {@link ServiceConfigurationError} as {@link ClassPathEnumerationError}.
 * 
 * @param <T>
 *            The type of the enumerated service.
 */
@PublicApi
public class ServiceLoaderClassPathServiceEnumerator<T> implements ClassPathServiceEnumerator<T>, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The class of the service.
	 */
	protected Class<? extends T> serviceClass;

	/**
	 * For {@link Externalizable}.
	 */
	public ServiceLoaderClassPathServiceEnumerator() {
	}

	/**
	 * Creates a new instance to enumerate the specified service type.
	 * 
	 * @param serviceClass
	 *            The service type.
	 * @throws NullPointerException
	 *             If the class is <code>null</code>.
	 */
	public ServiceLoaderClassPathServiceEnumerator(Class<? extends T> serviceClass) throws NullPointerException {
		Objects.requireNonNull(serviceClass, "service class");
		this.serviceClass = serviceClass;
	}

	/**
	 * Gets the service class which was used to create this instance.
	 * 
	 * @return The service class.
	 */
	public Class<? extends T> getServiceClass() {
		return serviceClass;
	}

	@Override
	public Iterable<? extends T> getServices(ClassLoader classloader) throws ClassPathEnumerationError {
		ServiceLoader<? extends T> iterable = ServiceLoader.load(serviceClass, classloader);
		return () -> new RethrowingServiceLoaderIterator<>(iterable);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(serviceClass);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		serviceClass = (Class<? extends T>) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((serviceClass == null) ? 0 : serviceClass.hashCode());
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
		ServiceLoaderClassPathServiceEnumerator<?> other = (ServiceLoaderClassPathServiceEnumerator<?>) obj;
		if (serviceClass == null) {
			if (other.serviceClass != null)
				return false;
		} else if (!serviceClass.equals(other.serviceClass))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + serviceClass + "]";
	}

	private static final class RethrowingServiceLoaderIterator<T> implements Iterator<T> {
		private final Iterator<? extends T> it;

		private RethrowingServiceLoaderIterator(ServiceLoader<? extends T> iterable) {
			this(iterable.iterator());
		}

		public RethrowingServiceLoaderIterator(Iterator<? extends T> it) {
			this.it = it;
		}

		@Override
		public T next() {
			try {
				return it.next();
			} catch (ServiceConfigurationError e) {
				throw new ClassPathEnumerationError(e);
			}
		}

		@Override
		public boolean hasNext() {
			try {
				return it.hasNext();
			} catch (ServiceConfigurationError e) {
				throw new ClassPathEnumerationError(e);
			}
		}
	}

}
