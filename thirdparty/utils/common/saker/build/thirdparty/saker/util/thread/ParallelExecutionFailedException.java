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
 * Exception thrown by task runners when a task failed to successfully complete, i.e. threw an exception.
 * <p>
 * This exception is specified to have no message, and no cause. The exceptions that caused this exception to be thrown
 * are suppressed and can be retrieved by calling {@link #getSuppressed()}.
 * <p>
 * The suppression mechanism was chosen to make the cause exceptions accessible, as multiple causes can be present, and
 * having only a single cause would be semantically incorrect.
 */
public class ParallelExecutionFailedException extends ParallelExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance, without cause or message.
	 * <p>
	 * The causes should be added as suppressed exceptions.
	 */
	public ParallelExecutionFailedException() {
		super(null, null, true, true);
	}
}