package saker.build.task.exception;

import saker.build.task.identifier.TaskIdentifier;

/**
 * Exception for representing that an operation was not allowed at the time of calling, with the given parameters if
 * any.
 * <p>
 * Usually a result of calling task context related methods after the execution of the corresponding task is over.
 * <p>
 * Also used when the some task operation requirements are violated. (E.g. a method which is supposed to be called once
 * is called more than once)
 */
public class IllegalTaskOperationException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	public IllegalTaskOperationException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, boolean, boolean, TaskIdentifier)
	 */
	protected IllegalTaskOperationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace, TaskIdentifier taskIdentifier) {
		super(message, cause, enableSuppression, writableStackTrace, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	public IllegalTaskOperationException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(TaskIdentifier)
	 */
	public IllegalTaskOperationException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	public IllegalTaskOperationException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}

}
