package saker.build.task.exception;

import saker.build.task.TaskContext;

/**
 * Signals that a state violation occurred when trying to access the standard IO lock for a task.
 * <p>
 * It is usually a result of trying to acquire the IO lock multiple times, trying to release it without acquiring
 * beforehand, or trying to read the standard input without acquiring it.
 * 
 * @see TaskContext#acquireStandardIOLock()
 * @see TaskContext#releaseStandardIOLock()
 * @see TaskContext#getStandardIn()
 */
public class TaskStandardIOLockIllegalStateException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IllegalStateException#IllegalStateException()
	 */
	public TaskStandardIOLockIllegalStateException() {
		super();
	}

	/**
	 * @see IllegalStateException#IllegalStateException(String)
	 */
	public TaskStandardIOLockIllegalStateException(String s) {
		super(s);
	}

}
