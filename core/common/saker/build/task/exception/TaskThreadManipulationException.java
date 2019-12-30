package saker.build.task.exception;

/**
 * Exception of this type is thrown when the build system encounters an unexpected scenario during dealing with task
 * threads.
 */
public class TaskThreadManipulationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public TaskThreadManipulationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected TaskThreadManipulationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public TaskThreadManipulationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public TaskThreadManipulationException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public TaskThreadManipulationException(Throwable cause) {
		super(cause);
	}

}
