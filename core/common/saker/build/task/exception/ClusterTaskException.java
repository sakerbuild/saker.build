package saker.build.task.exception;

/**
 * Common superclass for build cluster task invocation related exceptions.
 */
public class ClusterTaskException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public ClusterTaskException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected ClusterTaskException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public ClusterTaskException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public ClusterTaskException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException( Throwable)
	 */
	public ClusterTaskException(Throwable cause) {
		super(cause);
	}

}
