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

/**
 * Exception of this type is thrown when the build system encounters an unexpected scenario during dealing with task
 * threads.
 */
public class TaskThreadManipulationException extends TaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskException#TaskException()
	 */
	public TaskThreadManipulationException() {
		super();
	}

	/**
	 * @see TaskException#TaskException(String, Throwable, boolean)
	 */
	protected TaskThreadManipulationException(String message, Throwable cause, boolean writableStackTrace) {
		super(message, cause, writableStackTrace);
	}

	/**
	 * @see TaskException#TaskException(String, Throwable)
	 */
	public TaskThreadManipulationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see TaskException#TaskException(String)
	 */
	public TaskThreadManipulationException(String message) {
		super(message);
	}

	/**
	 * @see TaskException#TaskException(Throwable)
	 */
	public TaskThreadManipulationException(Throwable cause) {
		super(cause);
	}

}
