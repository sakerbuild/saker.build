package saker.build.task.exception;

import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * Common superclass for exceptions that are related to task execution and have a corresponding
 * {@linkplain TaskIdentifier task identifier}.
 */
public class TaskExecutionException extends TaskException {
	private static final int TASK_ID_TOSTRING_CHAR_LIMIT = 256;

	private static final long serialVersionUID = 1L;

	/**
	 * The task identifier.
	 */
	protected TaskIdentifier taskIdentifier;

	/**
	 * Creates a new exception for the given task identifier.
	 * 
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException()
	 */
	protected TaskExecutionException(TaskIdentifier taskIdentifier) {
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier and exception message.
	 * 
	 * @param message
	 *            The exception message.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(String)
	 */
	protected TaskExecutionException(String message, TaskIdentifier taskIdentifier) {
		super(message);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier and cause.
	 * 
	 * @param cause
	 *            The exception that caused this one.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(Throwable)
	 */
	protected TaskExecutionException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier, exception message, and cause.
	 * 
	 * @param message
	 *            The exception message.
	 * @param cause
	 *            The exception that caused this one.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(String, Throwable)
	 */
	protected TaskExecutionException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier with the specified initialization config.
	 * 
	 * @param message
	 *            The exception message.
	 * @param cause
	 *            The exception that caused this one.
	 * @param enableSuppression
	 *            Whether or not suppression is enabled or disabled.
	 * @param writableStackTrace
	 *            Whether or not the stack trace should be writable.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected TaskExecutionException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace, TaskIdentifier taskIdentifier) {
		super(message, cause, enableSuppression, writableStackTrace);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Gets the task identifier related to this exception.
	 * 
	 * @return The task identifier.
	 */
	public TaskIdentifier getTaskIdentifier() {
		return taskIdentifier;
	}

	@Override
	public String getMessage() {
		String result = super.getMessage();
		if (result == null) {
			return StringUtils.toStringLimit(taskIdentifier, TASK_ID_TOSTRING_CHAR_LIMIT, "...");
		}
		return result + " (" + StringUtils.toStringLimit(taskIdentifier, TASK_ID_TOSTRING_CHAR_LIMIT, "...") + ")";
	}
}
