package saker.build.thirdparty.saker.rmi.exception;

/**
 * Common superclass for all RMI related exceptions.
 * <p>
 * Throwing instances of this class manually might result in undefined operation of the RMI runtime.
 */
public class RMIRuntimeException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public RMIRuntimeException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected RMIRuntimeException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public RMIRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public RMIRuntimeException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public RMIRuntimeException(Throwable cause) {
		super(cause);
	}
}
