package saker.build.ide.support.persist;

public class DataFormatException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private StructuredDataType type;
	private transient Object value;

	public DataFormatException(String message) {
		super(message);
	}

	public DataFormatException(StructuredDataType type, Object value) {
		super("Failed to read data, invalid type: " + type);
		this.type = type;
		this.value = value;
	}

	public DataFormatException(StructuredDataType type, Object value, Throwable cause) {
		super("Failed to read data, invalid type: " + type, cause);
		this.type = type;
		this.value = value;
	}

	//doc: returns the actual type of the value
	//doc: null if protocol error, the value will be null then as well
	public StructuredDataType getType() {
		return type;
	}

	public Object getValue() {
		return value;
	}
}
