package saker.build.exception;

/**
 * Thrown when an argument path for a method is not in the expected format.
 * <p>
 * It is usually thrown when a method requires a path to be absolute or relative, but the caller fails to satisfy this
 * requirement.
 */
public class InvalidPathFormatException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IllegalArgumentException#IllegalArgumentException()
	 */
	public InvalidPathFormatException() {
		super();
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String, Throwable)
	 */
	public InvalidPathFormatException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String)
	 */
	public InvalidPathFormatException(String message) {
		super(message);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(Throwable)
	 */
	public InvalidPathFormatException(Throwable cause) {
		super(cause);
	}

}
