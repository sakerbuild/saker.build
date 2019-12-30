package saker.build.thirdparty.saker.rmi.io.writer;

import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;

/**
 * Writes the object as an {@link Enum} reference.
 * <p>
 * The given object is transferred as an {@link Enum} denoted by its name through the RMI streams.
 * <p>
 * It is to be noted that any state that the given enum instance has is not transferred, only a reference to its name.
 * <br>
 * Singletons which are backed by enum instances should not use this write handler. <br>
 * Stateless enumerations which have no specific implementation are a good candidate for this.
 * <p>
 * If the given object is not an instance of {@link Enum} then {@link RMIObjectTransferFailureException} is thrown.
 */
public final class EnumRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new EnumRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#enumWriter()} instead.
	 */
	public EnumRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.ENUM;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && this.getClass() == obj.getClass();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}
}