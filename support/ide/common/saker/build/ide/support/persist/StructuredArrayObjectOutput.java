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

public interface StructuredArrayObjectOutput extends Closeable {
	public void write(String value) throws IOException;

	public void write(boolean value) throws IOException;

	public void write(char value) throws IOException;

	public void write(byte value) throws IOException;

	public void write(short value) throws IOException;

	public void write(int value) throws IOException;

	public void write(long value) throws IOException;

	public void write(float value) throws IOException;

	public void write(double value) throws IOException;

	public StructuredObjectOutput writeObject() throws IOException;

	public StructuredArrayObjectOutput writeArray() throws IOException;
}
