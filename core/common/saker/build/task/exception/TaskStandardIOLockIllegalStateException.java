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

/**
 * Signals that a state violation occurred when trying to access the standard IO lock for a task.
 * <p>
 * It is usually a result of trying to acquire the IO lock multiple times, trying to release it without acquiring
 * beforehand, or trying to read the standard input without acquiring it.
 * 
 * @see TaskContext#acquireStandardIOLock()
 * @see TaskContext#releaseStandardIOLock()
 * @see TaskContext#getStandardIn()
 */
public class TaskStandardIOLockIllegalStateException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IllegalStateException#IllegalStateException()
	 */
	public TaskStandardIOLockIllegalStateException() {
		super();
	}

	/**
	 * @see IllegalStateException#IllegalStateException(String)
	 */
	public TaskStandardIOLockIllegalStateException(String s) {
		super(s);
	}

}
