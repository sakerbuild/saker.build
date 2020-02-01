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

import java.io.IOException;

/**
 * Exception thrown in case of transfer error.
 * <p>
 * This exception signals a channel error between the two endpoints of the connection. If the connection is closed, an
 * instance of this will be thrown.
 */
public class RMIIOFailureException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIIOFailureException(String message) {
		super(message);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIIOFailureException(IOException cause) {
		super(cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIIOFailureException(String message, IOException cause) {
		super(message, cause);
	}

	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}
}
