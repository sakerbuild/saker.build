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
package saker.build.ide.support.persist;

import java.io.Closeable;
import java.io.IOException;
import java.util.NoSuchElementException;

public interface StructuredArrayObjectInput extends Closeable {
	public int length();

	public StructuredDataType getNextDataType();

	public String readString() throws IOException, NoSuchElementException, DataFormatException;

	public Boolean readBoolean() throws IOException, NoSuchElementException, DataFormatException;

	public Character readChar() throws IOException, NoSuchElementException, DataFormatException;

	public Byte readByte() throws IOException, NoSuchElementException, DataFormatException;

	public Short readShort() throws IOException, NoSuchElementException, DataFormatException;

	public Integer readInt() throws IOException, NoSuchElementException, DataFormatException;

	public Long readLong() throws IOException, NoSuchElementException, DataFormatException;

	public Float readFloat() throws IOException, NoSuchElementException, DataFormatException;

	public Double readDouble() throws IOException, NoSuchElementException, DataFormatException;

	public StructuredObjectInput readObject() throws IOException, NoSuchElementException, DataFormatException;

	public StructuredArrayObjectInput readArray() throws IOException, NoSuchElementException, DataFormatException;
}
