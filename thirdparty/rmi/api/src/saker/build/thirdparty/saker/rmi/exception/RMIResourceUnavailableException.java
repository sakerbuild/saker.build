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
 * Exception thrown in case an associated RMI resource is not available.
 * <p>
 * This exception signals that a given RMI resource that is being accessed is not available, usually because it is
 * already in a closed state or being in the process of closing. The resource may be an <code>RMIVariables</code>,
 * <code>RMIConnection</code>, or other resource, which is described in the error message.
 * 
 * @since saker.rmi 0.8.3
 */
public class RMIResourceUnavailableException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIResourceUnavailableException(String message) {
		super(message);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIResourceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIResourceUnavailableException(Throwable cause) {
		super(cause);
	}

}
