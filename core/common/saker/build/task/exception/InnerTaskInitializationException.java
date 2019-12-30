package saker.build.task.exception;

/**
 * Exception thrown when the initialization of inner tasks fail.
 * <p>
 * This is usually happens when some unexpected error causes all of the suitable environments for the inner task to fail
 * starting the inner task.
 */
public class InnerTaskInitializationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected InnerTaskInitializationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public InnerTaskInitializationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public InnerTaskInitializationException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see TaskException#TaskException()
	 */
	public InnerTaskInitializationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public InnerTaskInitializationException(String message) {
		super(message);
	}

}
