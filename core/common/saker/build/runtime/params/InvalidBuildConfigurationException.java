package saker.build.runtime.params;

/**
 * Exceptions class representing the scenario when a build execution configuration is invalid.
 */
public class InvalidBuildConfigurationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public InvalidBuildConfigurationException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected InvalidBuildConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public InvalidBuildConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public InvalidBuildConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public InvalidBuildConfigurationException(Throwable cause) {
		super(cause);
	}

}
