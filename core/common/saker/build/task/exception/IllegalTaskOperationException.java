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
 * Exception for representing that an operation was not allowed at the time of calling, with the given parameters if
 * any.
 * <p>
 * Usually a result of calling task context related methods after the execution of the corresponding task is over.
 * <p>
 * Also used when the some task operation requirements are violated. (E.g. a method which is supposed to be called once
 * is called more than once)
 */
public class IllegalTaskOperationException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	public IllegalTaskOperationException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, boolean, TaskIdentifier)
	 */
	protected IllegalTaskOperationException(String message, Throwable cause, boolean writableStackTrace,
			TaskIdentifier taskIdentifier) {
		super(message, cause, writableStackTrace, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	public IllegalTaskOperationException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(TaskIdentifier)
	 */
	public IllegalTaskOperationException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	public IllegalTaskOperationException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}

}
