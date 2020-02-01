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

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIRedirect;

/**
 * Thrown if the RMI runtime detects invalid configuration for method calls or proxies.
 * <p>
 * An instance of this will be thrown if the possibility of an invalid configuration is detected. <br>
 * See the configuration classes and annotations for more information.
 * 
 * @see RMICacheResult
 * @see RMIDefaultOnFailure
 * @see RMIExceptionRethrow
 * @see RMIForbidden
 * @see RMIRedirect
 */
public class RMIInvalidConfigurationException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RMIRuntimeException#RMIRuntimeException()
	 */
	public RMIInvalidConfigurationException() {
		super();
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable, boolean, boolean)
	 */
	protected RMIInvalidConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String, Throwable)
	 */
	public RMIInvalidConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(String)
	 */
	public RMIInvalidConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see RMIRuntimeException#RMIRuntimeException(Throwable)
	 */
	public RMIInvalidConfigurationException(Throwable cause) {
		super(cause);
	}

}
