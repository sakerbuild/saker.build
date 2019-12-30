package saker.build.cache;

import java.util.Objects;

/**
 * Exception thrown when a build cache field was not found for the given name.
 */
public class CacheFieldNotFoundException extends BuildCacheException {
	private static final long serialVersionUID = 1L;

	private String fieldName;

	/**
	 * Creates a new exception initialized with the specified field name.
	 * 
	 * @param fieldName
	 *            The field name.
	 */
	public CacheFieldNotFoundException(String fieldName) {
		super(fieldName);
		Objects.requireNonNull(fieldName, "field name");
		this.fieldName = fieldName;
	}

	/**
	 * Gets the name of the field which was not found.
	 * 
	 * @return The field name.
	 */
	public String getFieldName() {
		return fieldName;
	}
}
