package saker.build.scripting.model.info;

import saker.build.scripting.model.FormattedTextContent;

/**
 * Common superinterface for script model elements which are documented.
 * <p>
 * Any script model element that holds documentational information are a subinterface of this interface.
 */
public interface InformationHolder {
	/**
	 * Gets documentational information about this script element.
	 * 
	 * @return The information, or <code>null</code> if it is not available or still loading.
	 */
	public default FormattedTextContent getInformation() {
		return null;
	}
}
