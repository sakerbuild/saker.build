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

/**
 * Thrown when an operation was requested on a context variable, but the variable was not found.
 * 
 * @since saker.rmi 0.8.3
 */
public class RMIContextVariableNotFoundException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIContextVariableNotFoundException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIContextVariableNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIContextVariableNotFoundException(String message) {
		super(message);
	}

}
