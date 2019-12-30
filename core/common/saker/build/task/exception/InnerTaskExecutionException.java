package saker.build.task.exception;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

/**
 * Exception holding the cause of an inner task execution failure.
 * <p>
 * When an inner task is invoked and it throws an exception during its invocation, it will be rethrown as an
 * {@link InnerTaskExecutionException} with its cause set to the original exception.
 * 
 * @see TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)
 * @see InnerTaskResultHolder
 */
public class InnerTaskExecutionException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected InnerTaskExecutionException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public InnerTaskExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public InnerTaskExecutionException(Throwable cause) {
		super(cause);
	}

}
