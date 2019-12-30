package saker.build.internal.scripting.language.exc;

public class BuildAbortedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public BuildAbortedException() {
		super();
	}

	protected BuildAbortedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public BuildAbortedException(String message, Throwable cause) {
		super(message, cause);
	}

	public BuildAbortedException(String message) {
		super(message);
	}

	public BuildAbortedException(Throwable cause) {
		super(cause);
	}

}
