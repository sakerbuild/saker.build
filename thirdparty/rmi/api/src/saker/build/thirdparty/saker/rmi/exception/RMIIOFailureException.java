package saker.build.thirdparty.saker.rmi.exception;

import java.io.IOException;

/**
 * Exception thrown in case of transfer error.
 * <p>
 * This exception signals a channel error between the two endpoints of the connection. If the connection is closed, an
 * instance of this will be thrown.
 */
public class RMIIOFailureException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIIOFailureException(String message) {
		super(message);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIIOFailureException(IOException cause) {
		super(cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIIOFailureException(String message, IOException cause) {
		super(message, cause);
	}

	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}
}
