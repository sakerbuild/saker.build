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
package saker.build.ide.support;

import saker.build.runtime.execution.SakerLog;

/**
 * Interface for displaying an exception to the user.
 * <p>
 * The presentation of the exceptions are implementation dependent, and can differ for each client plugin implemention.
 */
public interface ExceptionDisplayer {
	/**
	 * Displays the argument exception.
	 * 
	 * @param exc
	 *            The exception.
	 */
	public void displayException(Throwable exc);

	/**
	 * Displays the argument exception with the given severity and message.
	 * <p>
	 * The default implementation calls {@link #displayException(Throwable)}.
	 * 
	 * @param severity
	 *            The severity as declared in the <code>SEVERITY_*</code> constants in {@link SakerLog}.
	 * @param message
	 *            Optional message that describes the operation that failed, may be <code>null</code>.
	 * @param exc
	 *            The exception.
	 * @see SakerLog#SEVERITY_ERROR
	 * @see SakerLog#SEVERITY_WARNING
	 * @see SakerLog#SEVERITY_INFO
	 */
	public default void displayException(int severity, String message, Throwable exc) {
		displayException(exc);
	}
}
