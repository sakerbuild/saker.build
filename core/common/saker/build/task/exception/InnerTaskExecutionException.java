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

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

/**
 * Exception holding the cause of an inner task execution failure.
 * <p>
 * When an inner task is invoked and it throws an exception during its invocation, it will be rethrown as an
 * {@link InnerTaskExecutionException} with its cause set to the original exception.
 * 
 * @see TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)
 * @see InnerTaskResultHolder
 */
public class InnerTaskExecutionException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean, boolean)
	 */
	protected InnerTaskExecutionException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public InnerTaskExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public InnerTaskExecutionException(Throwable cause) {
		super(cause);
	}

}
