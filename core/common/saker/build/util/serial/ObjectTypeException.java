package saker.build.util.serial;

public class ObjectTypeException extends ObjectSerializationException {
	private static final long serialVersionUID = 1L;

	public ObjectTypeException() {
		super();
	}

	public ObjectTypeException(String message, Throwable cause) {
		super(message, cause);
	}

	public ObjectTypeException(String message) {
		super(message);
	}

	public ObjectTypeException(Throwable cause) {
		super(cause);
	}

}
