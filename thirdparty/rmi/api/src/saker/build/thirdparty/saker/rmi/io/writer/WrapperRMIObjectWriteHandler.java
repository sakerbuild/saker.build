package saker.build.thirdparty.saker.rmi.io.writer;

import java.lang.reflect.Modifier;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * Writes the object using the given {@link RMIWrapper} passed in the constructor.
 * <p>
 * An {@link RMIWrapper} will be instantiated to transfer the given object over RMI. For more information see
 * {@link RMIWrapper}.
 * <p>
 * In order to use this class with {@link RMIWriter}, subclass it, provide a no-arg default constructor which sets the
 * appropriate {@link RMIWrapper} class, and use it as a value for the annotation.
 * <p>
 * Or use {@link RMIWrap} annotation without any subclassing.
 * 
 * @see RMIWrap
 * @see RMIWrapper
 */
public class WrapperRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * The wrapper class which is used to transfer objects.
	 */
	protected final Class<? extends RMIWrapper> wrapperClass;

	/**
	 * Creates a new instance with the given wrapper class.
	 * 
	 * @param wrapperClass
	 *            The wrapper class.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the wrapper class is not a valid wrapper class. (If it is abstract, or enum.)
	 */
	public WrapperRMIObjectWriteHandler(Class<? extends RMIWrapper> wrapperClass)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(wrapperClass, "wrapper class");
		if (Modifier.isAbstract(wrapperClass.getModifiers())) {
			throw new IllegalArgumentException("RMI Wrapper class may not be abstract. " + wrapperClass);
		} else if (Enum.class.isAssignableFrom(wrapperClass)) {
			throw new IllegalArgumentException("RMI Wrapper class may not be an enum. " + wrapperClass);
		}
		this.wrapperClass = wrapperClass;
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.WRAPPER;
	}

	/**
	 * Gets the wrapper class that should be used to transfer objects.
	 * 
	 * @return The wrapper class.
	 */
	public final Class<? extends RMIWrapper> getWrapperClass() {
		return wrapperClass;
	}

	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((wrapperClass == null) ? 0 : wrapperClass.hashCode());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WrapperRMIObjectWriteHandler other = (WrapperRMIObjectWriteHandler) obj;
		if (wrapperClass == null) {
			if (other.wrapperClass != null)
				return false;
		} else if (!wrapperClass.equals(other.wrapperClass))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (wrapperClass != null ? "wrapperClass=" + wrapperClass : "") + "]";
	}

}
