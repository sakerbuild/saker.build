package saker.build.runtime.repository;

/**
 * Exception for reporting any repository related issues.
 * <p>
 * If a repository operations fails unexpectedly, and instance of this exception is thrown. Repositories may also throw
 * instances of this exception. E.g. {@link TaskNotFoundException}.
 * <p>
 * It is not required that this exception is a result of a call to a repository, instances of this can be thrown even
 * when no repositories have been loaded. E.g. if repositories are not available, but was required for some operation to
 * complete.
 */
public class RepositoryException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public RepositoryException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected RepositoryException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public RepositoryException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public RepositoryException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public RepositoryException(Throwable cause) {
		super(cause);
	}

}
