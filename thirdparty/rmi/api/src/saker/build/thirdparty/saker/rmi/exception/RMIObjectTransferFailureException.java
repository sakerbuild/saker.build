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
 * Thrown if objects for the RMI request fail to transfer.
 * <p>
 * If any parameter, return value, class, method, constructor, or other objects fail to be transferred to the remote
 * endpoint, an instance of this exception is thrown.
 * <p>
 * If the outbound transfer of an object succeeds but the reading fails on the other side, then
 * {@link RMICallFailedException} might be thrown instead.
 */
public class RMIObjectTransferFailureException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIObjectTransferFailureException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIObjectTransferFailureException(String message) {
		super(message);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIObjectTransferFailureException(Throwable cause) {
		super(cause);
	}

}
