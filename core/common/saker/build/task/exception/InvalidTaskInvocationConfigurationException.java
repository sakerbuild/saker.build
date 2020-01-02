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

import saker.build.task.TaskFactory;

/**
 * Thrown by the build system when an invalid configuration was detected for a task.
 * <p>
 * Usually thrown when conflicting {@linkplain TaskFactory#getCapabilities() capabilities} are reported from a
 * {@linkplain TaskFactory task factory}.
 */
public class InvalidTaskInvocationConfigurationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public InvalidTaskInvocationConfigurationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected InvalidTaskInvocationConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public InvalidTaskInvocationConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public InvalidTaskInvocationConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public InvalidTaskInvocationConfigurationException(Throwable cause) {
		super(cause);
	}
}
