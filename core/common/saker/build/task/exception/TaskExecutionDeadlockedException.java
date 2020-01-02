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
 * Exception thrown when the build system detects that a build execution has deadlocked.
 * <p>
 * Deadlocks can occur when no task threads can continue its job, as all of them in a waiting state for other tasks.
 * <p>
 * The build execution actively monitors this scenario.
 * <p>
 * Clients shouldn't try to recover from this kind of exception.
 */
public final class TaskExecutionDeadlockedException extends TaskResultWaitingFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskResultWaitingFailedException#TaskResultWaitingFailedException(TaskIdentifier)
	 */
	TaskExecutionDeadlockedException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

}
