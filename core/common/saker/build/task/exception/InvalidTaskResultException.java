package saker.build.task.exception;

import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Exception to represent the scenario when a task returns invalid value from its {@link Task#run(TaskContext)} method.
 * <p>
 * One scenario when this is thrown when the task calls {@link TaskContext#abortExecution(Throwable)}, and doesn't
 * return <code>null</code> from its worker method.
 * <p>
 * This exception can be also thrown when other methods of {@link TaskFactory} return invalid values.
 * <p>
 * See the exception message for more information.
 */
public class InvalidTaskResultException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	public InvalidTaskResultException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	public InvalidTaskResultException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(TaskIdentifier)
	 */
	public InvalidTaskResultException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	public InvalidTaskResultException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}

}
