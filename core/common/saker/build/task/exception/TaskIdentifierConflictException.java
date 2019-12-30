package saker.build.task.exception;

import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Instances of this exception is thrown when the task identifier and corresponding data uniqueness is violated.
 * <p>
 * This is usually thrown when a task for a specific identifier is started multiple times with different
 * {@linkplain TaskFactory task factories}.
 */
public class TaskIdentifierConflictException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	public TaskIdentifierConflictException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

}
