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

import saker.build.task.InnerTaskResultHolder;
import saker.build.task.Task;
import saker.build.task.TaskContext;

/**
 * The serialization of a task result failed.
 * <p>
 * This exception is thrown when the serialization of the output of a task failed between different endpoints. This
 * usually happens when the result of a remote dispatchable inner task cannot be transferred back to the coordinator
 * machine for some reason.
 * <p>
 * Note that when this exception is thrown, then the associated task might or might've not finished successfully.
 * <p>
 * See the {@linkplain #getCause() cause} for further information.
 * 
 * @see InnerTaskResultHolder#getResult()
 * @see Task#run(TaskContext)
 */
public class TaskResultSerializationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public TaskResultSerializationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean)
	 */
	protected TaskResultSerializationException(String message, Throwable cause, boolean writableStackTrace) {
		super(message, cause, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public TaskResultSerializationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public TaskResultSerializationException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public TaskResultSerializationException(Throwable cause) {
		super(cause);
	}

}
