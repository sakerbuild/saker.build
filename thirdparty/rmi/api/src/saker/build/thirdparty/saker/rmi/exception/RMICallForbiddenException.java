package saker.build.thirdparty.saker.rmi.exception;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Thrown if a method call on a remote proxy is not allowed.
 * <p>
 * If a method call is forbidden by its configuration, then an instance of this class will be thrown.
 * 
 * @see RMIForbidden
 * @see RMIDefaultOnFailure
 */
public class RMICallForbiddenException extends RMICallFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMICallFailedException#RMICallFailedException(Throwable)
	 */
	public RMICallForbiddenException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see RMICallFailedException#RMICallFailedException(String, Throwable)
	 */
	public RMICallForbiddenException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMICallFailedException#RMICallFailedException(String)
	 */
	public RMICallForbiddenException(String message) {
		super(message);
	}

}
