package saker.build.exception;

/**
 * Exception for signaling that the build execution failed.
 * <p>
 * This exception is not used in general, only when the build is started by an external agent. E.g. when the build is
 * directly run via the command line, this exception is thrown from the main method of the launcher.
 */
public class BuildExecutionFailedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public BuildExecutionFailedException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected BuildExecutionFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public BuildExecutionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public BuildExecutionFailedException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public BuildExecutionFailedException(Throwable cause) {
		super(cause);
	}

}
