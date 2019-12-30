package saker.build.thirdparty.saker.rmi.io.writer;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * RMI object write strategy defining interface.
 * <p>
 * Write handlers can be bound to method parameters and their results. Handlers are used to define the strategy how the
 * given objects should be transferred over the RMI connection. Each implementation of this interface has an
 * {@link ObjectWriterKind} given to it. The RMI streams decide a writing strategy based on this kind. For explanation
 * of each kind, visit the corresponding {@link RMIObjectWriteHandler} implementation for them.
 * <p>
 * Implementers of this interface should adhere to the <code>equals</code> and <code>hashCode</code> contract defined by
 * {@link Object}.
 * <p>
 * While write handlers can be used to specialize the object transfer, some aspects of it cannot be modified:
 * <ul>
 * <li>Transferring <code>null</code> will always result in <code>null</code> on the other endpoint.</li>
 * <li>Transferring a remote object will result in the corresponding local instance on the other endpoint.</li>
 * <li>Primitive types, boxed primitives, and {@link String} instances are transferred as-is.</li>
 * <li>Primitive arrays are transferred as-is.</li>
 * <li>{@link Void} is always transferred as <code>null</code>.</li>
 * <li>If transfer configuration was defined for the transferred object class then the write handler for that
 * configuration is used to override the transfer.</li>
 * </ul>
 * <p>
 * This interface should not be directly implemented by users but users should use and extend already implemented
 * versions of this interface in this package.
 * 
 * @see ObjectWriterKind
 * @see RMIWriter
 */
public interface RMIObjectWriteHandler {
	/**
	 * Gets the kind of this object write handler.
	 * 
	 * @return The kind.
	 */
	public ObjectWriterKind getKind();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	/**
	 * Gets a common instance for {@link DefaultRMIObjectWriteHandler}.
	 * 
	 * @return The common instance.
	 */
	public static RMIObjectWriteHandler defaultWriter() {
		return DefaultRMIObjectWriteHandler.INSTANCE;
	}

	/**
	 * Gets a common instance for {@link RemoteRMIObjectWriteHandler}.
	 * 
	 * @return The common instance.
	 */
	public static RMIObjectWriteHandler remote() {
		return RemoteRMIObjectWriteHandler.INSTANCE;
	}

	/**
	 * Gets a common instance for {@link RemoteOnlyRMIObjectWriteHandler}.
	 * 
	 * @return The common instance.
	 */
	public static RMIObjectWriteHandler remoteOnly() {
		return RemoteOnlyRMIObjectWriteHandler.INSTANCE;
	}

	/**
	 * Gets a common instance for {@link SerializeRMIObjectWriteHandler}.
	 * 
	 * @return The common instance.
	 */
	public static RMIObjectWriteHandler serialize() {
		return SerializeRMIObjectWriteHandler.INSTANCE;
	}

	/**
	 * Gets a common instance for {@link EnumRMIObjectWriteHandler}.
	 * 
	 * @return The common instance.
	 */
	public static RMIObjectWriteHandler enumWriter() {
		return EnumRMIObjectWriteHandler.INSTANCE;
	}

	/**
	 * Utility method for constructing {@link WrapperRMIObjectWriteHandler}.
	 * 
	 * @param wrapper
	 *            The wrapper class.
	 * @return The resulting write handler.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument class is not a valid wrapper class. (If it is abstract, or enum.)
	 */
	public static RMIObjectWriteHandler wrapper(Class<? extends RMIWrapper> wrapper)
			throws NullPointerException, IllegalArgumentException {
		return new WrapperRMIObjectWriteHandler(wrapper);
	}

	/**
	 * Utility method for constructing {@link ArrayComponentRMIObjectWriteHandler}.
	 * 
	 * @param componenthandler
	 *            The component write handler.
	 * @return The resulting write handler.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static RMIObjectWriteHandler array(RMIObjectWriteHandler componenthandler) throws NullPointerException {
		return new ArrayComponentRMIObjectWriteHandler(componenthandler);
	}

}
