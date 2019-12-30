package saker.build.runtime.execution;

public class ExecutionInitializationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ExecutionInitializationException() {
		super();
	}

	public ExecutionInitializationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ExecutionInitializationException(String message, Throwable cause) {
		super(message, cause);
	}

	public ExecutionInitializationException(String message) {
		super(message);
	}

	public ExecutionInitializationException(Throwable cause) {
		super(cause);
	}

}
