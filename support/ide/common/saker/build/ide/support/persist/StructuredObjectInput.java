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
import java.util.Set;

public interface StructuredObjectInput extends Closeable {
	public Set<String> getFields();

	//doc: read methods return null if no value found for field
	//doc: if multiple values found then an arbitrary one is returned

	public String readString(String name) throws IOException, DataFormatException;

	public Boolean readBoolean(String name) throws IOException, DataFormatException;

	public Character readChar(String name) throws IOException, DataFormatException;

	public Byte readByte(String name) throws IOException, DataFormatException;

	public Short readShort(String name) throws IOException, DataFormatException;

	public Integer readInt(String name) throws IOException, DataFormatException;

	public Long readLong(String name) throws IOException, DataFormatException;

	public Float readFloat(String name) throws IOException, DataFormatException;

	public Double readDouble(String name) throws IOException, DataFormatException;

	public StructuredObjectInput readObject(String name) throws IOException, DataFormatException;

	public StructuredArrayObjectInput readArray(String name) throws IOException, DataFormatException;
}
