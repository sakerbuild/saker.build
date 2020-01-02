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
 * Interface for querying information for an associated token.
 * <p>
 * The values of this interface may be lazily populated.
 * <p>
 * The token informations are usually displayed when the user hovers over a given token in the code editor, or
 * explicitly requests information about it.
 * 
 * @see ScriptSyntaxModel#getTokenInformation(ScriptToken)
 * @see SimpleScriptTokenInformation
 */
public interface ScriptTokenInformation {
	/**
	 * Gets the description for the corresponding token if available.
	 * <p>
	 * This is usually displayed when an user hovers the mouse pointer over a script token in the IDE, or explicitly
	 * requests information about it.
	 * 
	 * @return The description.
	 */
	public PartitionedTextContent getDescription();

	/**
	 * Gets the identifier that is associated with this token information schema.
	 * <p>
	 * The schema identifiers are arbitrary strings that should uniquely identify the nature of the token information.
	 * It can be used by IDE plugins and others to interpret the token information and present the user a more readable
	 * display.
	 * <p>
	 * One use case for this is to create IDE plugins that add various icons for the information display.
	 * <p>
	 * E.g.:
	 * 
	 * <pre>
	 * "org.company.scripting.token.info"
	 * </pre>
	 * 
	 * @return The schema identifier or <code>null</code> if none.
	 */
	public default String getSchemaIdentifier() {
		return null;
	}

	/**
	 * Gets the schema meta-data that is associated with the token information.
	 * <p>
	 * The meta-data can contain arbitrary key-value pairs that can be used to describe various aspects of the
	 * information. This is used to convey information to the IDE plugins about different aspects of the token
	 * information.
	 * 
	 * @return The meta-data for the token information. May be <code>null</code> or empty.
	 * @see #getSchemaIdentifier()
	 */
	public default Map<String, String> getSchemaMetaData() {
		return Collections.emptyMap();
	}
}
