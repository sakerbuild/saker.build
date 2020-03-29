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
package saker.build.task.exception;

/**
 * Common superclass for all exceptions that are thrown during build execution.
 * <p>
 * Suppression is always enabled for {@link TaskException} subclasses.
 */
public class TaskException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	protected TaskException() {
		super();
	}

	/**
	 * Constructs a new instance with the specified detail message, cause, and writable stack trace enabled or disabled.
	 * <p>
	 * The suppression is always enabled for {@link TaskException} instances.
	 * 
	 * @param message
	 *            the detail message.
	 * @param cause
	 *            the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or
	 *            unknown.)
	 * @param writableStackTrace
	 *            whether or not the stack trace should be writable
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 * @since saker.build 0.8.11
	 */
	protected TaskException(String message, Throwable cause, boolean writableStackTrace) {
		super(message, cause, true, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	protected TaskException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	protected TaskException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	protected TaskException(Throwable cause) {
		super(cause);
	}

}
