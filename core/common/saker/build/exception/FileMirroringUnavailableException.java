package saker.build.exception;

/**
 * Thrown when a file mirroring request couldn't be satisfied due to the mirror directory not being available.
 * <p>
 * This is often due to not specifying the mirror directory for a build execution (or cluster daemon).
 */
public class FileMirroringUnavailableException extends MissingConfigurationException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see MissingConfigurationException#MissingConfigurationException()
	 */
	public FileMirroringUnavailableException() {
		super();
	}

	/**
	 * @see MissingConfigurationException#MissingConfigurationException(String, Throwable)
	 */
	public FileMirroringUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see MissingConfigurationException#MissingConfigurationException(String)
	 */
	public FileMirroringUnavailableException(String message) {
		super(message);
	}

	/**
	 * @see MissingConfigurationException#MissingConfigurationException(Throwable)
	 */
	public FileMirroringUnavailableException(Throwable cause) {
		super(cause);
	}

}
