package saker.build.thirdparty.saker.util.io;

import java.io.IOException;

/**
 * Exception signaling that an operation failed due to an underlying network connection failure.
 */
public class RemoteIOException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IOException#IOException()
	 */
	public RemoteIOException() {
		super();
	}

	/**
	 * @see IOException#IOException(String, Throwable)
	 */
	public RemoteIOException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IOException#IOException(String)
	 */
	public RemoteIOException(String message) {
		super(message);
	}

	/**
	 * @see IOException#IOException( Throwable)
	 */
	public RemoteIOException(Throwable cause) {
		super(cause);
	}

}
