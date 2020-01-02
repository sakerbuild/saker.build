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
package saker.build.scripting.model;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a text partition with given title, subtitle, and contents.
 * <p>
 * Some elements of the text partition may be <code>null</code>, but not all of them.
 * 
 * @see SimpleTextPartition
 */
public interface TextPartition {
	/**
	 * Gets the title of the partition.
	 * 
	 * @return The title, or <code>null</code> if not defined.
	 */
	public String getTitle();

	/**
	 * Gets the subtitle of the partition.
	 * 
	 * @return The subtitle, or <code>null</code> if not defined.
	 */
	public default String getSubTitle() {
		return null;
	}

	/**
	 * Gets the text body content for the partition.
	 * 
	 * @return The contents of the partition, or <code>null</code> if not defined.
	 */
	public FormattedTextContent getContent();

	/**
	 * Gets the identifier that is associated with this text partition schema.
	 * <p>
	 * The schema identifiers are arbitrary strings that should uniquely identify the nature of the text partition. It
	 * can be used by IDE plugins and others to interpret the text partition and present the user a more readable
	 * display.
	 * <p>
	 * One use case for this is to create IDE plugins that add various icons for the text display.
	 * <p>
	 * E.g.:
	 * 
	 * <pre>
	 * "org.company.scripting.text"
	 * </pre>
	 * 
	 * @return The schema identifier or <code>null</code> if none.
	 */
	public default String getSchemaIdentifier() {
		return null;
	}

	/**
	 * Gets the schema meta-data that is associated with the text partition.
	 * <p>
	 * The meta-data can contain arbitrary key-value pairs that can be used to describe various aspects of the
	 * partition. This is used to convey information to the IDE plugins about different aspects of the text partition.
	 * 
	 * @return The meta-data for the text partition. May be <code>null</code> or empty.
	 * @see #getSchemaIdentifier()
	 */
	public default Map<String, String> getSchemaMetaData() {
		return Collections.emptyMap();
	}

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}