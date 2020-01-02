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
package saker.build.runtime.environment;

import saker.apiextract.api.PublicApi;
import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.task.TaskResultCollection;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.util.exc.ExceptionView;

/**
 * Interface representing the execution result of a build task execution request.
 * <p>
 * This class holds the completion information about a build execution. The collection of task results, the result code
 * (kind), any occurred exceptions or script trace can be retrieved using the appropriate functions.
 */
@PublicApi
public interface BuildTaskExecutionResult {
	/**
	 * Enumeration for the possible exit codes that a build task execution can end with.
	 */
	public enum ResultKind {
		/**
		 * The build execution was successful, no exception was thrown during task execution.
		 * <p>
		 * Exceptions might still be thrown after the tasks have finished, make sure to check
		 * {@link BuildTaskExecutionResult#getExceptionView()}.
		 */
		SUCCESSFUL(true),
		/**
		 * The build execution failed, an exception was thrown during task execution.
		 * <p>
		 * See {@link BuildTaskExecutionResult#getExceptionView()} for more information.
		 */
		FAILURE(false),
		/**
		 * The build system failed to initialize the build execution.
		 * <p>
		 * See {@link BuildTaskExecutionResult#getExceptionView()} for more information.
		 */
		INITIALIZATION_ERROR(false),

		;

		private final boolean successful;

		private ResultKind(boolean successful) {
			this.successful = successful;
		}

		/**
		 * Gets if the build execution should be considered successful.
		 * <p>
		 * A build is successful, if no tasks threw an exception during their execution.
		 * 
		 * @return <code>true</code> if successful.
		 */
		public boolean isSuccessful() {
			return successful;
		}

	}

	/**
	 * Gets the result kind of this build execution result.
	 * <p>
	 * The result kind is basically the exit code for the build execution.
	 * 
	 * @return The result kind.
	 */
	@RMICacheResult
	public ResultKind getResultKind();

	/**
	 * Gets the task result collection of the execution, which holds the results and related data about the executed
	 * tasks.
	 * 
	 * @return The task result collection.
	 */
	@RMICacheResult
	public TaskResultCollection getTaskResultCollection();

	/**
	 * Gets the exception view of the occurred exception.
	 * <p>
	 * It is recommended to call this instead of {@link #getException()}, as the Throwable instance might not be RMI
	 * transferrable.
	 * 
	 * @return The exception view or <code>null</code> if no exception was thrown.
	 * @see #getException()
	 * @see #getPositionedExceptionView()
	 */
	@RMICacheResult
	public default ExceptionView getExceptionView() {
		return getPositionedExceptionView();
	}

	/**
	 * Gets the exception view of the occurred exception.
	 * <p>
	 * This exception view holds and script traces about the occurred exception if available.
	 * <p>
	 * It is recommended to call this instead of {@link #getException()}, as the Throwable instance might not be RMI
	 * transferrable.
	 * 
	 * @return The exception view or <code>null</code> if no exception was thrown.
	 * @see #getException()
	 */
	@RMICacheResult
	public ScriptPositionedExceptionView getPositionedExceptionView();

	/**
	 * Gets the exception instance that was thrown during execution.
	 * <p>
	 * There may be an exception even if the {@linkplain #getResultKind() result kind} is successful.
	 * <p>
	 * It is recommended that callers use {@link #getPositionedExceptionView()} instead, as the Throwable instance might
	 * not be RMI transferrable.
	 * 
	 * @return The exception instance or <code>null</code> if no exception was thrown.
	 */
	@RMICacheResult
	@RMISerialize
	public Throwable getException();
}
