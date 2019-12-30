package saker.build.task.exception;

/**
 * Exception signaling that a task execution on a build cluster failed.
 */
public class ClusterTaskExecutionFailedException extends ClusterTaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see ClusterTaskException#ClusterTaskException()
	 */
	public ClusterTaskExecutionFailedException() {
		super();
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(String, Throwable, boolean, boolean)
	 */
	protected ClusterTaskExecutionFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException( Throwable)
	 */
	public ClusterTaskExecutionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(String)
	 */
	public ClusterTaskExecutionFailedException(String message) {
		super(message);
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(Throwable)
	 */
	public ClusterTaskExecutionFailedException(Throwable cause) {
		super(cause);
	}

}
