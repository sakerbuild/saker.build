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

import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Exception can be thrown by tasks when they detect that the execution was cancelled by user request.
 * <p>
 * If tasks throw this exception, their execution will be considered as failed. Instead, they are recommended to handle
 * cancellation gracefully, and recover if possible. They can use {@link TaskContext#abortExecution(Throwable)} to
 * signal cancellation, while completing the execution partially and handling partial results in the next incremental
 * build.
 */
public class TaskExecutionCancelledException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskResultWaitingFailedException#TaskResultWaitingFailedException(TaskIdentifier)
	 */
	public TaskExecutionCancelledException(TaskIdentifier taskIdentifier) {
		super("Execution cancelled by user request.", taskIdentifier);
	}

}
