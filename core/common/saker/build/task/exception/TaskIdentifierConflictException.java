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
import saker.build.task.identifier.TaskIdentifier;

/**
 * Instances of this exception is thrown when the task identifier and corresponding data uniqueness is violated.
 * <p>
 * This is usually thrown when a task for a specific identifier is started multiple times with different
 * {@linkplain TaskFactory task factories}.
 */
public class TaskIdentifierConflictException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskExecutionException#TaskExecutionException(String, TaskIdentifier)
	 */
	public TaskIdentifierConflictException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

}
