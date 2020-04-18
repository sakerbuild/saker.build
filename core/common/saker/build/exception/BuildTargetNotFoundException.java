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
 * A build target was not found in a given context.
 * <p>
 * Instances of this exception is usually thrown when a build target is not found with a given name in a specified build
 * script. It may also be thrown when the script file itself is not found.
 * 
 * @since saker.build 0.8.11
 */
public class BuildTargetNotFoundException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IllegalArgumentException#IllegalArgumentException()
	 */
	public BuildTargetNotFoundException() {
		super();
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String, Throwable)
	 */
	public BuildTargetNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String)
	 */
	public BuildTargetNotFoundException(String message) {
		super(message);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(Throwable)
	 */
	public BuildTargetNotFoundException(Throwable cause) {
		super(cause);
	}

}
