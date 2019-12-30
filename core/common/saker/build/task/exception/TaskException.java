package saker.build.task.exception;

/**
 * Common superclass for all exceptions that are thrown during build execution.
 */
public class TaskException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	protected TaskException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected TaskException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	protected TaskException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	protected TaskException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	protected TaskException(Throwable cause) {
		super(cause);
	}

}
