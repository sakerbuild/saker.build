package saker.build.task.exception;

import saker.build.task.identifier.TaskIdentifier;

/**
 * Common superclass for exceptions that signal an error during waiting for a specified task.
 * <p>
 * The exact nature of the exception is specified by the subclass. Instances of this exception is usually thrown when
 * waiting for a task fails due to circumstances that the waiting task can't control.
 * 
 * @see TaskExecutionDeadlockedException
 * @see TaskResultWaitingInterruptedException
 */
public class TaskResultWaitingFailedException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, boolean, boolean, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace, TaskIdentifier taskIdentifier) {
		super(message, cause, enableSuppression, writableStackTrace, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}
}
