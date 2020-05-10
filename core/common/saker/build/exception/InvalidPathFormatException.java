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
package saker.build.exception;

/**
 * Thrown when a path or path name is not in the expected format.
 * <p>
 * It is usually thrown when a method requires a path to be absolute or relative, but the caller fails to satisfy this
 * requirement.
 */
public class InvalidPathFormatException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IllegalArgumentException#IllegalArgumentException()
	 */
	public InvalidPathFormatException() {
		super();
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String, Throwable)
	 */
	public InvalidPathFormatException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String)
	 */
	public InvalidPathFormatException(String message) {
		super(message);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(Throwable)
	 */
	public InvalidPathFormatException(Throwable cause) {
		super(cause);
	}

}
