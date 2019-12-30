package saker.build.thirdparty.saker.util.thread;

/**
 * Exception thrown by multi-threadedly run tasks to abort any further task running.
 * <p>
 * An exception of this type can be thrown by concurrently run tasks to signal that further tasks should not be run by
 * the runner.
 * <p>
 * The exception handling of this type may not be supported in all runner contexts, see the documentations for the
 * runners in order to check if it can be used.
 */
public class ParallelExecutionAbortedException extends ParallelExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see ParallelExecutionException#ParallelExecutionException()
	 */
	public ParallelExecutionAbortedException() {
		super();
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(String, Throwable)
	 */
	public ParallelExecutionAbortedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(String)
	 */
	public ParallelExecutionAbortedException(String message) {
		super(message);
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(Throwable)
	 */
	public ParallelExecutionAbortedException(Throwable cause) {
		super(cause);
	}

}