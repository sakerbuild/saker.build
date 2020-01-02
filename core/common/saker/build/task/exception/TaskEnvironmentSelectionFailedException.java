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

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskExecutionEnvironmentSelector;

/**
 * Exception signalling that the build system failed to find a suitable build environment.
 * <p>
 * This can happen if {@link TaskExecutionEnvironmentSelector#isSuitableExecutionEnvironment(SakerEnvironment)} never
 * chooses a suitable environment for any of the passed environments.
 */
public class TaskEnvironmentSelectionFailedException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public TaskEnvironmentSelectionFailedException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected TaskEnvironmentSelectionFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public TaskEnvironmentSelectionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public TaskEnvironmentSelectionFailedException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException( Throwable)
	 */
	public TaskEnvironmentSelectionFailedException(Throwable cause) {
		super(cause);
	}

}
