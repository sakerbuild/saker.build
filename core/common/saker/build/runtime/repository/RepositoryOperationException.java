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
package saker.build.runtime.repository;

/**
 * Thrown when a call to a repository failed to execute in an unexpected way.
 */
public class RepositoryOperationException extends RepositoryException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RepositoryException#RepositoryException()
	 */
	public RepositoryOperationException() {
		super();
	}

	/**
	 * @see RepositoryException#RepositoryException(String, Throwable, boolean, boolean)
	 */
	protected RepositoryOperationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RepositoryException#RepositoryException(String, Throwable)
	 */
	public RepositoryOperationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RepositoryException#RepositoryException(String)
	 */
	public RepositoryOperationException(String message) {
		super(message);
	}

	/**
	 * @see RepositoryException#RepositoryException(Throwable)
	 */
	public RepositoryOperationException(Throwable cause) {
		super(cause);
	}

}
