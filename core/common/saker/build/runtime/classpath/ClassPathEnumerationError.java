package saker.build.runtime.classpath;

import java.util.ServiceConfigurationError;

/**
 * An instance of this exception can be thrown when a classpath service enumeration failed for any reason.
 * <p>
 * Although this class is named as <code>Error</code>, it extends {@link RuntimeException}. It is named as
 * <code>Error</code> to achieve similarity to {@link ServiceConfigurationError}, as both exceptions work in a very
 * similar way.
 * <p>
 * It is recommended that clients provide a meaningful message and cause of exception when instantiating this class.
 */
public class ClassPathEnumerationError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception without any information.
	 */
	public ClassPathEnumerationError() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected ClassPathEnumerationError(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public ClassPathEnumerationError(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public ClassPathEnumerationError(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public ClassPathEnumerationError(Throwable cause) {
		super(cause);
	}
}
