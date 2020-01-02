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
 * Exception thrown when the initialization of inner tasks fail.
 * <p>
 * This is usually happens when some unexpected error causes all of the suitable environments for the inner task to fail
 * starting the inner task.
 */
public class InnerTaskInitializationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected InnerTaskInitializationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public InnerTaskInitializationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public InnerTaskInitializationException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see TaskException#TaskException()
	 */
	public InnerTaskInitializationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public InnerTaskInitializationException(String message) {
		super(message);
	}

}
