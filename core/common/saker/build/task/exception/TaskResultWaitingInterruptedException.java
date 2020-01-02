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
 * Exception thrown when the thread is {@linkplain Thread#interrupt() interrupted} during waiting for a task to finish.
 * <p>
 * The thread interruption flag is unchanged (remains interrupted) when this exception is thrown.
 */
public final class TaskResultWaitingInterruptedException extends TaskResultWaitingFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskResultWaitingFailedException#TaskResultWaitingFailedException(TaskIdentifier)
	 */
	TaskResultWaitingInterruptedException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}
}
