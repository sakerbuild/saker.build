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
package saker.build.thirdparty.saker.util.thread;

/**
 * Exception representing that the task running has been cancelled by an external source.
 * <p>
 * This exception can be thrown when the task runner supports cancellation, and at least one task was cancelled.
 */
public class ParallelExecutionCancelledException extends ParallelExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see ParallelExecutionException#ParallelExecutionException()
	 */
	public ParallelExecutionCancelledException() {
		super();
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(String, Throwable)
	 */
	public ParallelExecutionCancelledException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(String)
	 */
	public ParallelExecutionCancelledException(String message) {
		super(message);
	}

	/**
	 * @see ParallelExecutionException#ParallelExecutionException(Throwable)
	 */
	public ParallelExecutionCancelledException(Throwable cause) {
		super(cause);
	}

}