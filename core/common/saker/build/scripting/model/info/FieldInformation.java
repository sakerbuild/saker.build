package saker.build.scripting.model.info;

import saker.build.scripting.model.FormattedTextContent;

/**
 * Interface holding information about a given field.
 * <p>
 * Fields are members of an enclosing type with a unique name in its enclosing context. Each field has a type.
 * 
 * @see SimpleFieldInformation
 */
public interface FieldInformation extends InformationHolder {
	/**
	 * Gety the name of this field.
	 * 
	 * @return The name.
	 */
	public String getName();

	/**
	 * Gets the type of the field.
	 * 
	 * @return The type of the field or <code>null</code> if not available.
	 */
	public TypeInformation getType();

	/**
	 * Gets documentational information about this field.
	 * 
	 * @return The information.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}

	/**
	 * Gets if the field is deprecated.
	 * 
	 * @return <code>true</code> if the field is deprecated.
	 */
	public default boolean isDeprecated() {
		return false;
	}
}
