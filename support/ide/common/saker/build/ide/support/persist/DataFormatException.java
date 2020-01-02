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

public class DataFormatException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private StructuredDataType type;
	private transient Object value;

	public DataFormatException(String message) {
		super(message);
	}

	public DataFormatException(StructuredDataType type, Object value) {
		super("Failed to read data, invalid type: " + type);
		this.type = type;
		this.value = value;
	}

	public DataFormatException(StructuredDataType type, Object value, Throwable cause) {
		super("Failed to read data, invalid type: " + type, cause);
		this.type = type;
		this.value = value;
	}

	//doc: returns the actual type of the value
	//doc: null if protocol error, the value will be null then as well
	public StructuredDataType getType() {
		return type;
	}

	public Object getValue() {
		return value;
	}
}
