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

/**
 * A script token represents a single entity in the source code that is handled as a unit.
 * <p>
 * Script tokens have a region that they take up in the source code. This region is specified by an offset and length.
 * <p>
 * Script tokens have a type which can be used to handle them differently with its enclosing model. E.g. syntax
 * highlighting. If two tokens have the same type, they should be displayed the same way in an IDE. Token types are
 * arbitrary strings defined by their enclosing model.
 * <p>
 * Token regions should not overlap in a given script model.
 */
public interface ScriptToken {
	/**
	 * Gets the offset of the token in the associated document.
	 * <p>
	 * The offset is zero based and inclusive.
	 * 
	 * @return The offset.
	 */
	public int getOffset();

	/**
	 * Gets the length of the token.
	 * <p>
	 * The length defines how many characters a token take up in the source code.
	 * <p>
	 * A token length may be zero.
	 * 
	 * @return The length.
	 */
	public int getLength();

	/**
	 * Checks if this token takes up any characters.
	 * <p>
	 * The default implementation simply checks if {@link #getLength()} is zero.
	 * 
	 * @return
	 */
	public default boolean isEmpty() {
		return getLength() == 0;
	}

	/**
	 * Gets the region end offset of this token.
	 * <p>
	 * The returned value is and exclusive offset.
	 * <p>
	 * The default implementation returns
	 * 
	 * <pre>
	 * getOffset() + getLength()
	 * </pre>
	 * 
	 * @return The end offset.
	 */
	public default int getEndOffset() {
		return getOffset() + getLength();
	}

	/**
	 * Gets the type of this token.
	 * <p>
	 * The type can be an arbitrary string, in relation with the enclosing {@linkplain ScriptSyntaxModel script model}.
	 * 
	 * @return The type.
	 */
	public String getType();
}