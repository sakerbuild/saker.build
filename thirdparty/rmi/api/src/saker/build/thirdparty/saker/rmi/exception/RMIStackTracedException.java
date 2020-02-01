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
package saker.build.thirdparty.saker.rmi.exception;

import java.lang.reflect.InvocationTargetException;

/**
 * Exception to hold the originating information for exceptions which fail to transfer over RMI.
 * <p>
 * If an exception happens during an RMI call it is transferred to the other side. It can happen that the serialization
 * of the causing exception fails. In that case an instance of this class is created and serialized over the connection.
 * The message and stack trace is the same as the originating exception, but the exact exception type is not preserved.
 * <br>
 * Suppressed exceptions are transferred as an instance of this class as well.
 */
public class RMIStackTracedException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance.
	 * <p>
	 * The message, causes, suppressed exceptions, and stacktrace will be recursively copied from the argument
	 * exception.
	 * 
	 * @param cause
	 *            The exception to copy.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public RMIStackTracedException(Throwable cause) throws NullPointerException {
		super(cause.toString(), getCauseOfCause(cause));
		setStackTrace(cause.getStackTrace());
		addSuppresseds(this, cause);
	}

	private static RMIStackTracedException getCauseOfCause(Throwable cause) {
		if (cause == null) {
			return null;
		}
		Throwable directcause = cause.getCause();
		if (directcause != null) {
			return new RMIStackTracedException(directcause);
		}
		if (cause instanceof InvocationTargetException) {
			return new RMIStackTracedException(((InvocationTargetException) cause).getTargetException());
		}
		return null;
	}

	private static void addSuppresseds(Throwable target, Throwable cause) {
		for (Throwable sup : cause.getSuppressed()) {
			target.addSuppressed(new RMIStackTracedException(sup));
		}
	}

}
