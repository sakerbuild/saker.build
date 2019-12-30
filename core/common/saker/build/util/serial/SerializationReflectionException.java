package saker.build.util.serial;

public class SerializationReflectionException extends ObjectSerializationException {
	private static final long serialVersionUID = 1L;

	public SerializationReflectionException() {
		super();
	}

	public SerializationReflectionException(String message, Throwable cause) {
		super(message, cause);
	}

	public SerializationReflectionException(String message) {
		super(message);
	}

	public SerializationReflectionException(Throwable cause) {
		super(cause);
	}

}
