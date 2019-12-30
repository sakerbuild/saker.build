package saker.build.task.exception;

import saker.build.task.ParameterizableTask;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Exceptions signaling that some issue was encountered when dealing with task parameters.
 * <p>
 * The nature of the issue should be included in the message in the exception.
 * 
 * @see ParameterizableTask
 */
public class TaskParameterException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	public TaskParameterException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	public TaskParameterException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	public TaskParameterException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}

}
