package saker.build.thirdparty.saker.util.thread;

/**
 * Exception representing that the task running has been cancelled by an external source.
 * <p>
 * This exception can be thrown when the task runner supports cancellation, and at least one task was cancelled.
 */
public class ParallelExecutionCancelledException extends ParallelExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see ParallelExecutionException#ParallelExecutionException()
	 */
	public ParallelExecutionCancelledException() {
		super();
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(String, Throwable)
	 */
	public ParallelExecutionCancelledException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(String)
	 */
	public ParallelExecutionCancelledException(String message) {
		super(message);
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(Throwable)
	 */
	public ParallelExecutionCancelledException(Throwable cause) {
		super(cause);
	}

}