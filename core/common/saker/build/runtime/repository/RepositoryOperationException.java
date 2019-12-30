package saker.build.runtime.repository;

/**
 * Thrown when a call to a repository failed to execute in an unexpected way.
 */
public class RepositoryOperationException extends RepositoryException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RepositoryException#RepositoryException()
	 */
	public RepositoryOperationException() {
		super();
	}

	/**
	 * @see RepositoryException#RepositoryException(String, Throwable, boolean, boolean)
	 */
	protected RepositoryOperationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RepositoryException#RepositoryException(String, Throwable)
	 */
	public RepositoryOperationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RepositoryException#RepositoryException(String)
	 */
	public RepositoryOperationException(String message) {
		super(message);
	}

	/**
	 * @see RepositoryException#RepositoryException(Throwable)
	 */
	public RepositoryOperationException(Throwable cause) {
		super(cause);
	}

}
