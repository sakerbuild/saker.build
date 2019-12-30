package saker.build.task.exception;

import saker.build.task.identifier.TaskIdentifier;

/**
 * Exceptions representing that a task execution did not finish successfully.
 * <p>
 * Clients shouldn't create new instances and throw this kind exception, it is used by the build system.
 * <p>
 * The {@link #getCause()} holds the exception thrown by the specified task.
 */
public final class TaskExecutionFailedException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	TaskExecutionFailedException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(null, cause, true, true, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	TaskExecutionFailedException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, true, true, taskIdentifier);
	}

}
