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

public interface StructuredObjectOutput extends Closeable {
	public void writeField(String name) throws IOException, DuplicateObjectFieldException;
	
	public void writeField(String name, String value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, boolean value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, char value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, byte value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, short value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, int value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, long value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, float value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, double value) throws IOException, DuplicateObjectFieldException;

	public StructuredObjectOutput writeObject(String name) throws IOException, DuplicateObjectFieldException;

	public StructuredArrayObjectOutput writeArray(String name) throws IOException, DuplicateObjectFieldException;
}
