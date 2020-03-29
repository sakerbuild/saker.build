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
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * Common superclass for exceptions that are related to task execution and have a corresponding
 * {@linkplain TaskIdentifier task identifier}.
 */
public class TaskExecutionException extends TaskException {
	private static final int TASK_ID_TOSTRING_CHAR_LIMIT = 256;

	private static final long serialVersionUID = 1L;

	/**
	 * The task identifier.
	 */
	protected TaskIdentifier taskIdentifier;

	/**
	 * Creates a new exception for the given task identifier.
	 * 
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException()
	 */
	protected TaskExecutionException(TaskIdentifier taskIdentifier) {
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier and exception message.
	 * 
	 * @param message
	 *            The exception message.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(String)
	 */
	protected TaskExecutionException(String message, TaskIdentifier taskIdentifier) {
		super(message);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier and cause.
	 * 
	 * @param cause
	 *            The exception that caused this one.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(Throwable)
	 */
	protected TaskExecutionException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier, exception message, and cause.
	 * 
	 * @param message
	 *            The exception message.
	 * @param cause
	 *            The exception that caused this one.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(String, Throwable)
	 */
	protected TaskExecutionException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Creates a new exception for the given task identifier with the specified initialization config.
	 * 
	 * @param message
	 *            The exception message.
	 * @param cause
	 *            The exception that caused this one.
	 * @param enableSuppression
	 *            Whether or not suppression is enabled or disabled.
	 * @param writableStackTrace
	 *            Whether or not the stack trace should be writable.
	 * @param taskIdentifier
	 *            The related task identifier.
	 * @see TaskException#TaskException(String, Throwable, boolean)
	 */
	protected TaskExecutionException(String message, Throwable cause, boolean writableStackTrace,
			TaskIdentifier taskIdentifier) {
		super(message, cause, writableStackTrace);
		this.taskIdentifier = taskIdentifier;
	}

	/**
	 * Gets the task identifier related to this exception.
	 * 
	 * @return The task identifier.
	 */
	public TaskIdentifier getTaskIdentifier() {
		return taskIdentifier;
	}

	@Override
	public String getMessage() {
		String result = super.getMessage();
		if (result == null) {
			return StringUtils.toStringLimit(taskIdentifier, TASK_ID_TOSTRING_CHAR_LIMIT, "...");
		}
		return result + " (" + StringUtils.toStringLimit(taskIdentifier, TASK_ID_TOSTRING_CHAR_LIMIT, "...") + ")";
	}
}
