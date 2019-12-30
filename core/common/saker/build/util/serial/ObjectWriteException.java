package saker.build.util.serial;

public class ObjectWriteException extends ObjectSerializationException {
	private static final long serialVersionUID = 1L;

	public ObjectWriteException() {
		super();
	}

	public ObjectWriteException(String message, Throwable cause) {
		super(message, cause);
	}

	public ObjectWriteException(String message) {
		super(message);
	}

	public ObjectWriteException(Throwable cause) {
		super(cause);
	}

}
