package saker.build.ide.support.persist;

public class DuplicateObjectFieldException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	private String fieldName;

	public DuplicateObjectFieldException(String fieldName) {
		super(fieldName);
		this.fieldName = fieldName;
	}

	public String getFieldName() {
		return fieldName;
	}
}
