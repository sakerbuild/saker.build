package saker.build.util.cache;

import saker.apiextract.api.PublicApi;

/**
 * Thrown when a violation was found in a {@link CacheKey} implementation.
 */
@PublicApi
public class InvalidCacheKeyImplementationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public InvalidCacheKeyImplementationException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected InvalidCacheKeyImplementationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public InvalidCacheKeyImplementationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public InvalidCacheKeyImplementationException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public InvalidCacheKeyImplementationException(Throwable cause) {
		super(cause);
	}

}
