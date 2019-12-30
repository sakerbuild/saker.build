package saker.build.task.exception;

import saker.build.task.identifier.TaskIdentifier;

/**
 * Exception thrown when the thread is {@linkplain Thread#interrupt() interrupted} during waiting for a task to finish.
 * <p>
 * The thread interruption flag is unchanged (remains interrupted) when this exception is thrown.
 */
public final class TaskResultWaitingInterruptedException extends TaskResultWaitingFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskResultWaitingFailedException#TaskResultWaitingFailedException(TaskIdentifier)
	 */
	TaskResultWaitingInterruptedException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}
}
