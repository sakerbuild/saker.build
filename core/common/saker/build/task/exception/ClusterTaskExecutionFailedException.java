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
 * Exception signaling that a task execution on a build cluster failed.
 */
public class ClusterTaskExecutionFailedException extends ClusterTaskException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see ClusterTaskException#ClusterTaskException()
	 */
	public ClusterTaskExecutionFailedException() {
		super();
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(String, Throwable, boolean)
	 */
	protected ClusterTaskExecutionFailedException(String message, Throwable cause, boolean writableStackTrace) {
		super(message, cause, writableStackTrace);
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(Throwable)
	 */
	public ClusterTaskExecutionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(String)
	 */
	public ClusterTaskExecutionFailedException(String message) {
		super(message);
	}

	/**
	 * @see ClusterTaskException#ClusterTaskException(Throwable)
	 */
	public ClusterTaskExecutionFailedException(Throwable cause) {
		super(cause);
	}

}
