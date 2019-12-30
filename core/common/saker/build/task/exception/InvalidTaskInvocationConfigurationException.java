package saker.build.task.exception;

import saker.build.task.TaskFactory;

/**
 * Thrown by the build system when an invalid configuration was detected for a task.
 * <p>
 * Usually thrown when conflicting {@linkplain TaskFactory#getCapabilities() capabilities} are reported from a
 * {@linkplain TaskFactory task factory}.
 */
public class InvalidTaskInvocationConfigurationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public InvalidTaskInvocationConfigurationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected InvalidTaskInvocationConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public InvalidTaskInvocationConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public InvalidTaskInvocationConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public InvalidTaskInvocationConfigurationException(Throwable cause) {
		super(cause);
	}
}
