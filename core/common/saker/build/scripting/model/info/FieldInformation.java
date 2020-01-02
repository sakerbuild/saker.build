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
