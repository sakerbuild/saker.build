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

import saker.build.task.identifier.TaskIdentifier;

/**
 * Exceptions representing that a task execution did not finish successfully.
 * <p>
 * Clients shouldn't create new instances and throw this kind exception, it is used by the build system.
 * <p>
 * The {@link #getCause()} holds the exception thrown by the specified task.
 */
public final class TaskExecutionFailedException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	TaskExecutionFailedException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(null, cause, true, true, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	TaskExecutionFailedException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, true, true, taskIdentifier);
	}

}
