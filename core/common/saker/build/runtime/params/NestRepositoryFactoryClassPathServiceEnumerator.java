package saker.build.runtime.params;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.classpath.ClassPathEnumerationError;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.classpath.NamedCheckingClassPathServiceEnumerator;
import saker.build.runtime.repository.SakerRepositoryFactory;

/**
 * {@link ClassPathServiceEnumerator} implementation for locating the {@link SakerRepositoryFactory} service from the
 * Nest repositors class path.
 * 
 * @see #getInstance()
 * @see NestRepositoryClassPathLocation
 */
public class NestRepositoryFactoryClassPathServiceEnumerator
		implements ClassPathServiceEnumerator<SakerRepositoryFactory>, Externalizable {
	private static final long serialVersionUID = 1L;

	private ClassPathServiceEnumerator<? extends SakerRepositoryFactory> realEnumerator;

	/**
	 * Gets an instance of this service enumerator.
	 * <p>
	 * The result may or may not be a singleton instance.
	 * 
	 * @return An instance.
	 */
	public static ClassPathServiceEnumerator<? extends SakerRepositoryFactory> getInstance() {
		NestRepositoryFactoryClassPathServiceEnumerator result = new NestRepositoryFactoryClassPathServiceEnumerator();
		result.realEnumerator = new NamedCheckingClassPathServiceEnumerator<>("saker.nest.NestRepositoryFactory",
				SakerRepositoryFactory.class);
		return result;
	}

	/**
	 * For {@link Externalizable}.
	 * 
	 * @deprecated Use {@link #getInstance()}.
	 */
	//deprecate so the compiler warns about calling it. Only getInstance() should be used to ensure 
	//compatibility between versions
	@Deprecated
	public NestRepositoryFactoryClassPathServiceEnumerator() {
	}

	@Override
	public Iterable<? extends SakerRepositoryFactory> getServices(ClassLoader classloader)
			throws ClassPathEnumerationError {
		return realEnumerator.getServices(classloader);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(realEnumerator);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		realEnumerator = (ClassPathServiceEnumerator<? extends SakerRepositoryFactory>) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((realEnumerator == null) ? 0 : realEnumerator.hashCode());
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
		NestRepositoryFactoryClassPathServiceEnumerator other = (NestRepositoryFactoryClassPathServiceEnumerator) obj;
		if (realEnumerator == null) {
			if (other.realEnumerator != null)
				return false;
		} else if (!realEnumerator.equals(other.realEnumerator))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + realEnumerator + "]";
	}
}
