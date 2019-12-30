package saker.build.internal.scripting.language.exc;

public class InvalidScriptDeclarationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public InvalidScriptDeclarationException() {
		super();
	}

	protected InvalidScriptDeclarationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public InvalidScriptDeclarationException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidScriptDeclarationException(String message) {
		super(message);
	}

	public InvalidScriptDeclarationException(Throwable cause) {
		super(cause);
	}

}
