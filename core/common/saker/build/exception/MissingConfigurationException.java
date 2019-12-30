package saker.build.exception;

/**
 * Exception thrown when a feature required by a task is not available due to missing configuration by the user.
 */
public class MissingConfigurationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public MissingConfigurationException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected MissingConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public MissingConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public MissingConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public MissingConfigurationException(Throwable cause) {
		super(cause);
	}

}
