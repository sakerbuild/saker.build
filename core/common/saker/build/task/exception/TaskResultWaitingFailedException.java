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
 * Common superclass for exceptions that signal an error during waiting for a specified task.
 * <p>
 * The exact nature of the exception is specified by the subclass. Instances of this exception is usually thrown when
 * waiting for a task fails due to circumstances that the waiting task can't control.
 * 
 * @see TaskExecutionDeadlockedException
 * @see TaskResultWaitingInterruptedException
 */
public class TaskResultWaitingFailedException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, boolean, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(String message, Throwable cause, boolean writableStackTrace,
			TaskIdentifier taskIdentifier) {
		super(message, cause, writableStackTrace, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, Throwable, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	/**
	 * @see TaskExecutionException#TaskExecutionException(Throwable, TaskIdentifier)
	 */
	protected TaskResultWaitingFailedException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}
}
