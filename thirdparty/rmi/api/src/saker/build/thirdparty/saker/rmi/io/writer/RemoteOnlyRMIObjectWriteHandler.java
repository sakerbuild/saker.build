package saker.build.thirdparty.saker.rmi.io.writer;

import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;

/**
 * Writes the object only if it is remote, else throws {@link RMIObjectTransferFailureException}.
 * <p>
 * RMI write handler strategy for transferring an object only if it is a remote object to the other endpoint. If it is
 * not, then a {@link RMIObjectTransferFailureException} will be thrown, and the request fails.
 */
public final class RemoteOnlyRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new RemoteOnlyRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#remoteOnly()} instead.
	 */
	public RemoteOnlyRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.REMOTE_ONLY;
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