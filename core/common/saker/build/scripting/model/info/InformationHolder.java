/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
