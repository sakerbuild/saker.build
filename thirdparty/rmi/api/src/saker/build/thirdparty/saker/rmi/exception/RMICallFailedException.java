package saker.build.thirdparty.saker.rmi.exception;

/**
 * Thrown if an RMI request fails for some reason.
 * <p>
 * This exception can be thrown for various reasons by the RMI runtime. See the message, cause and suppressed exceptions
 * of the instance for more information about its cause.
 */
public class RMICallFailedException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMICallFailedException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMICallFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMICallFailedException(String message) {
		super(message);
	}

}
