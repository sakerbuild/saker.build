package saker.build.thirdparty.saker.util.thread;

/**
 * Exception thrown by task runners when a task failed to successfully complete, i.e. threw an exception.
 * <p>
 * This exception is specified to have no message, and no cause. The exceptions that caused this exception to be thrown
 * are suppressed and can be retrieved by calling {@link #getSuppressed()}.
 * <p>
 * The suppression mechanism was chosen to make the cause exceptions accessible, as multiple causes can be present, and
 * having only a single cause would be semantically incorrect.
 */
public class ParallelExecutionFailedException extends ParallelExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance, without cause or message.
	 * <p>
	 * The causes should be added as suppressed exceptions.
	 */
	public ParallelExecutionFailedException() {
		super(null, null, true, true);
	}
}