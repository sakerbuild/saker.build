package saker.build.util.serial;

import java.io.IOException;

public class SerializationProtocolException extends IOException {
	private static final long serialVersionUID = 1L;

	public SerializationProtocolException() {
		super();
	}

	public SerializationProtocolException(String message, Throwable cause) {
		super(message, cause);
	}

	public SerializationProtocolException(String message) {
		super(message);
	}

	public SerializationProtocolException(Throwable cause) {
		super(cause);
	}

}
