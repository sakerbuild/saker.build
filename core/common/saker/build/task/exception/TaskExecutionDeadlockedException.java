package saker.build.task.exception;

import saker.build.task.identifier.TaskIdentifier;

/**
 * Exception thrown when the build system detects that a build execution has deadlocked.
 * <p>
 * Deadlocks can occur when no task threads can continue its job, as all of them in a waiting state for other tasks.
 * <p>
 * The build execution actively monitors this scenario.
 * <p>
 * Clients shouldn't try to recover from this kind of exception.
 */
public final class TaskExecutionDeadlockedException extends TaskResultWaitingFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskResultWaitingFailedException#TaskResultWaitingFailedException(TaskIdentifier)
	 */
	TaskExecutionDeadlockedException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

}
