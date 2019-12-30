package saker.build.runtime.params;

/**
 * Exception representing the scenario when a file system path is accessible through multiple root paths.
 * <p>
 * This can happen when such a configuration is defined that a path is accessible from multiple roots. <br>
 * Example:
 * <ul>
 * <li><code>c:/users</code> is mounted to <code>u:</code></li>
 * <li><code>c:/users/john</code> is mounted to <code>j:</code></li>
 * </ul>
 * In the above example <code>c:/users/john</code> is accessible directly through the root <code>j:</code> and the via
 * path <code>u:/john</code> using the root <code>j:</code>.
 * <p>
 * This configuration is invalid, as it would allow for multiple file representation in the build system that are
 * conflicting.
 */
public class AmbiguousPathConfigurationException extends InvalidBuildConfigurationException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException()
	 */
	public AmbiguousPathConfigurationException() {
		super();
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(String, Throwable, boolean, boolean)
	 */
	protected AmbiguousPathConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(String, Throwable)
	 */
	public AmbiguousPathConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(String)
	 */
	public AmbiguousPathConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(Throwable)
	 */
	public AmbiguousPathConfigurationException(Throwable cause) {
		super(cause);
	}

}
