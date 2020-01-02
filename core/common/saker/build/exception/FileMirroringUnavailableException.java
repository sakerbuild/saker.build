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
 * Thrown when a file mirroring request couldn't be satisfied due to the mirror directory not being available.
 * <p>
 * This is often due to not specifying the mirror directory for a build execution (or cluster daemon).
 */
public class FileMirroringUnavailableException extends MissingConfigurationException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see MissingConfigurationException#MissingConfigurationException()
	 */
	public FileMirroringUnavailableException() {
		super();
	}

	/**
	 * @see MissingConfigurationException#MissingConfigurationException(String, Throwable)
	 */
	public FileMirroringUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see MissingConfigurationException#MissingConfigurationException(String)
	 */
	public FileMirroringUnavailableException(String message) {
		super(message);
	}

	/**
	 * @see MissingConfigurationException#MissingConfigurationException(Throwable)
	 */
	public FileMirroringUnavailableException(Throwable cause) {
		super(cause);
	}

}
