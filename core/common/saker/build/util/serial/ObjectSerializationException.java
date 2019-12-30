package saker.build.util.serial;

import java.io.IOException;

public abstract class ObjectSerializationException extends IOException {
	private static final long serialVersionUID = 1L;

	public ObjectSerializationException() {
		super();
	}

	public ObjectSerializationException(String message, Throwable cause) {
		super(message, cause);
	}

	public ObjectSerializationException(String message) {
		super(message);
	}

	public ObjectSerializationException(Throwable cause) {
		super(cause);
	}

}
