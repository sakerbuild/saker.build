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
package saker.build.exception;

import java.util.Objects;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;

/**
 * Exception type thrown when a property in the build system is failed to compute properly.
 * <p>
 * When the current value is being computed, it is possible that the operation throws an exception. In that case that
 * exception is forwarded to the caller by wrapping it into an instance of {@link PropertyComputationFailedException}.
 * <p>
 * This is in order to be able to cache the results of a computation, and always provide a consistent interface for the
 * computations.
 * <p>
 * The originally thrown exception can be retrieved by calling {@link #getCause()}.
 * 
 * @see EnvironmentProperty
 * @see ExecutionProperty
 * @see SakerEnvironment#getEnvironmentPropertyCurrentValue(EnvironmentProperty)
 * @see ExecutionContext#getExecutionPropertyCurrentValue(ExecutionProperty)
 */
public class PropertyComputationFailedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected PropertyComputationFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, Objects.requireNonNull(cause, "cause"), enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public PropertyComputationFailedException(String message, Throwable cause) {
		super(message, Objects.requireNonNull(cause, "cause"));
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public PropertyComputationFailedException(Throwable cause) {
		super(Objects.requireNonNull(cause, "cause"));
	}

	/**
	 * Gets the exception that caused the failure of the property value computation.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public Throwable getCause() {
		return super.getCause();
	}

}
