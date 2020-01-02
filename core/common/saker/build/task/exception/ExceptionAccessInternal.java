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

import saker.apiextract.api.ExcludeApi;
import saker.build.task.identifier.TaskIdentifier;

@ExcludeApi
public class ExceptionAccessInternal {
	private ExceptionAccessInternal() {
		throw new UnsupportedOperationException();
	}

	public static TaskExecutionFailedException createTaskExecutionFailedException(Throwable cause,
			TaskIdentifier taskid) {
		return new TaskExecutionFailedException(cause, taskid);
	}

	public static TaskExecutionFailedException createTaskExecutionFailedException(String message, Throwable cause,
			TaskIdentifier taskid) {
		return new TaskExecutionFailedException(message, cause, taskid);
	}

	public static MultiTaskExecutionFailedException createMultiTaskExecutionFailedException(TaskIdentifier taskid) {
		return new MultiTaskExecutionFailedException(taskid);
	}

	public static void addMultiTaskExecutionFailedCause(MultiTaskExecutionFailedException exc, TaskIdentifier taskid,
			TaskException cause) {
		exc.addException(taskid, cause);
	}

	public static TaskExecutionDeadlockedException createTaskExecutionDeadlockedException(TaskIdentifier taskid) {
		return new TaskExecutionDeadlockedException(taskid);
	}

	public static TaskResultWaitingInterruptedException createTaskResultWaitingInterruptedException(
			TaskIdentifier taskid) {
		return new TaskResultWaitingInterruptedException(taskid);
	}

}
