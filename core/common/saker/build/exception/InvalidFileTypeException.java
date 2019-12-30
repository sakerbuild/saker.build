package saker.build.exception;

import java.io.IOException;

/**
 * Exception representing a scenario when the expected, and actual type of a given file doesn't equal.
 */
public class InvalidFileTypeException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IOException#IOException()
	 */
	public InvalidFileTypeException() {
		super();
	}

	/**
	 * @see IOException#IOException(String, Throwable)
	 */
	public InvalidFileTypeException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IOException#IOException(String)
	 */
	public InvalidFileTypeException(String message) {
		super(message);
	}

	/**
	 * @see IOException#IOException( Throwable)
	 */
	public InvalidFileTypeException(Throwable cause) {
		super(cause);
	}

}
