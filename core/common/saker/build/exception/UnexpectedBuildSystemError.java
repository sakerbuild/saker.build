package saker.build.exception;

/**
 * Exception for signaling that the build system caught an exception that should not happen under normal circumstances.
 * <p>
 * Although this exceptions is named as <code>Error</code>, it is a subclass of {@link RuntimeException}.
 * <p>
 * When this kind of exception is thrown, it is strongly recommended to report it to the maintainers, and try doing a
 * clean build if the problem persists.
 */
public class UnexpectedBuildSystemError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public UnexpectedBuildSystemError() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected UnexpectedBuildSystemError(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public UnexpectedBuildSystemError(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public UnexpectedBuildSystemError(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public UnexpectedBuildSystemError(Throwable cause) {
		super(cause);
	}

}
