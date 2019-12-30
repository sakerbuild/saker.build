package saker.build.task.exception;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskExecutionEnvironmentSelector;

/**
 * Exception signalling that the build system failed to find a suitable build environment.
 * <p>
 * This can happen if {@link TaskExecutionEnvironmentSelector#isSuitableExecutionEnvironment(SakerEnvironment)} never
 * chooses a suitable environment for any of the passed environments.
 */
public class TaskEnvironmentSelectionFailedException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public TaskEnvironmentSelectionFailedException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected TaskEnvironmentSelectionFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public TaskEnvironmentSelectionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public TaskEnvironmentSelectionFailedException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException( Throwable)
	 */
	public TaskEnvironmentSelectionFailedException(Throwable cause) {
		super(cause);
	}

}
