package saker.build.thirdparty.saker.rmi.exception;

/**
 * Exception thrown if a remote proxy creation fails.
 * <p>
 * Creating of a remote proxy can fail in the following cases:
 * <ul>
 * <li>Methods with same signature have conflicting declaration, but are present in different hierarchies. These
 * problems are usually discovered in compile time. <br>
 * E.g. A method is declared in multiple super interfaces, but have different return types. <br>
 * E.g. A method has multiple default implementations. <br>
 * E.g. A method is decalred in multiple super interfaces and have different configurations. <br>
 * </li>
 * </ul>
 */
public class RMIProxyCreationFailedException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException()
	 */
	public RMIProxyCreationFailedException() {
		super();
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable, boolean, boolean)
	 */
	protected RMIProxyCreationFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIProxyCreationFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIProxyCreationFailedException(String message) {
		super(message);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIProxyCreationFailedException(Throwable cause) {
		super(cause);
	}

}
