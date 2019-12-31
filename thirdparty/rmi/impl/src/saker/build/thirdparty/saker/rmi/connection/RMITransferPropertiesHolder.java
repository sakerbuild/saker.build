package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Common superclass for different internal implementations of RMI properties collection.
 * <p>
 * This class is not indented for external use. To configure your RMI connection use {@link RMITransferProperties}.
 */
public abstract class RMITransferPropertiesHolder {
	/**
	 * Package private constructor to allow subclassing only by the RMI implementation.
	 */
	RMITransferPropertiesHolder() {
	}

	/**
	 * Gets the specified properties for the given method.
	 * 
	 * @param method
	 *            The method to lookup the properties for.
	 * @return The found properties or <code>null</code>.
	 */
	public abstract MethodTransferProperties getExecutableProperties(Method method);

	/**
	 * Gets the specified properties for the given constructor.
	 * 
	 * @param <C>
	 *            The declaring class of the constructor.
	 * @param constructor
	 *            The constructor to lookup the properties for.
	 * @return The found properties or <code>null</code>.
	 */
	public abstract <C> ConstructorTransferProperties<C> getExecutableProperties(Constructor<C> constructor);

	/**
	 * Gets the specified properties for the given class.
	 * 
	 * @param <C>
	 *            The type of the class.
	 * @param clazz
	 *            The class to lookup the properties for.
	 * @return The found properties or <code>null</code>.
	 */
	public abstract <C> ClassTransferProperties<C> getClassProperties(Class<C> clazz);
}
