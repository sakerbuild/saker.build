package saker.build.scripting.model.info;

import saker.build.scripting.model.FormattedTextContent;

/**
 * Contains information about a parameter of a build target.
 * 
 * @see BuildTargetInformation
 * @see BuildTargetInformation#getParameters()
 * @since saker.build 0.8.18
 */
public interface BuildTargetParameterInformation extends InformationHolder {
	/**
	 * Input parameter type.
	 * 
	 * @see #getType()
	 */
	public static final String TYPE_INPUT = "IN";
	/**
	 * Output parameter type.
	 * 
	 * @see #getType()
	 */
	public static final String TYPE_OUTPUT = "OUT";

	/**
	 * Gets the name of the parameter.
	 * 
	 * @return The name.
	 */
	public String getParameterName();

	/**
	 * Gets the type of the parameter.
	 * <p>
	 * The type is one of the constants declared in this interface named <code>TYPE_*</code>. The list of constants can
	 * be extended in the future, or may have script modelling specific meanings.
	 * 
	 * @return The type or <code>null</code> if not available.
	 * @see BuildTargetParameterInformation#TYPE_INPUT
	 * @see BuildTargetParameterInformation#TYPE_OUTPUT
	 */
	public default String getType() {
		return null;
	}

	/**
	 * Gets documentational information about this build target parameter.
	 * 
	 * @return The information.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}
}
