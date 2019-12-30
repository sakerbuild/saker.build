package saker.build.util.serial;

public class ObjectReadException extends ObjectSerializationException {
	private static final long serialVersionUID = 1L;

	public ObjectReadException(String message, Throwable cause) {
		super(message, cause);
	}

	public ObjectReadException(Throwable cause) {
		super(cause);
	}

}
