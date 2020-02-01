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

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Thrown if a method call on a remote proxy is not allowed.
 * <p>
 * If a method call is forbidden by its configuration, then an instance of this class will be thrown.
 * 
 * @see RMIForbidden
 * @see RMIDefaultOnFailure
 */
public class RMICallForbiddenException extends RMICallFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMICallFailedException#RMICallFailedException(Throwable)
	 */
	public RMICallForbiddenException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see RMICallFailedException#RMICallFailedException(String, Throwable)
	 */
	public RMICallForbiddenException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMICallFailedException#RMICallFailedException(String)
	 */
	public RMICallForbiddenException(String message) {
		super(message);
	}

}
