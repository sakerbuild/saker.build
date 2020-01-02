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
import saker.build.scripting.model.ScriptCompletionProposal;

/**
 * Provides information about a specific script literal.
 * <p>
 * Literals are the unit of information that can be declared in a script source code. This interface can provide
 * information about a literal and its type.
 * 
 * @see SimpleLiteralInformation
 */
public interface LiteralInformation extends InformationHolder {
	/**
	 * Gets a string representation of the literal.
	 * 
	 * @return The string representation of the literal.
	 */
	public String getLiteral();

	/**
	 * Gets information about this literal.
	 * <p>
	 * The information should contain general description about the literal, its context, and what it represents.
	 * 
	 * @return The information about this literal.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}

	/**
	 * Gets the relation of this literal.
	 * <p>
	 * Relation is a short information about associated with the literal. It is optional and in most cases the
	 * {@linkplain #getType() type information} is enough. However, in some cases it might be useful for the user to
	 * provide additional information related to the literal.
	 * <p>
	 * The returned string should be short, fit on a single line.
	 * 
	 * @return The relation of the literal or <code>null</code> if none.
	 * @see ScriptCompletionProposal#getDisplayRelation()
	 */
	public default String getRelation() {
		return null;
	}

	/**
	 * Gets the type of the literal.
	 * 
	 * @return The type of the literal or <code>null</code> if not available.
	 */
	public default TypeInformation getType() {
		return null;
	}

	/**
	 * Gets if the literal is deprecated.
	 * 
	 * @return <code>true</code> if the literal is deprecated.
	 */
	public default boolean isDeprecated() {
		return false;
	}
}