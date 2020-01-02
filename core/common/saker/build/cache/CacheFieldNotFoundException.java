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
