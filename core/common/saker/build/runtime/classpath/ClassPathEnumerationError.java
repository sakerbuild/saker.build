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
package saker.build.runtime.classpath;

import java.util.ServiceConfigurationError;

/**
 * An instance of this exception can be thrown when a classpath service enumeration failed for any reason.
 * <p>
 * Although this class is named as <code>Error</code>, it extends {@link RuntimeException}. It is named as
 * <code>Error</code> to achieve similarity to {@link ServiceConfigurationError}, as both exceptions work in a very
 * similar way.
 * <p>
 * It is recommended that clients provide a meaningful message and cause of exception when instantiating this class.
 */
public class ClassPathEnumerationError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception without any information.
	 */
	public ClassPathEnumerationError() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected ClassPathEnumerationError(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public ClassPathEnumerationError(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public ClassPathEnumerationError(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public ClassPathEnumerationError(Throwable cause) {
		super(cause);
	}
}
