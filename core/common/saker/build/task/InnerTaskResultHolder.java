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
package saker.build.task;

import saker.build.task.exception.InnerTaskExecutionException;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Interface holding the results of an inner task invocation.
 * <p>
 * The result of an inner task invocation is either successful or failed. <br>
 * If it was successful, {@link #getResult()} will return the object that was returned from the
 * {@link Task#run(TaskContext)} method. <br>
 * If not, then {@link #getResult()} will throw an appropriate exception, and {@link #getExceptionIfAny()} will return
 * the exception that was thrown by the task.
 * <p>
 * If the callers are only interested if the task finished successfully, they should use {@link #getExceptionIfAny()},
 * which can avoid throwing unnecessary exceptions.
 * 
 * @param <R>
 *            The type of the task result.
 */
public interface InnerTaskResultHolder<R> {
	/**
	 * Gets the result of the inner task execution, or throws an exception if it threw an exception.
	 * <p>
	 * This method will return the object that was returned from the {@link Task#run(TaskContext)} method of the inner
	 * task. If the call threw an exception before finishing properly, this method will throw an
	 * {@link InnerTaskExecutionException} with its cause set to the thrown exception.
	 * <p>
	 * If the inner task failed by calling {@link TaskContext#abortExecution(Throwable)}, then this method will still
	 * return the object that the inner task returned from {@link Task#run(TaskContext)}. <br>
	 * To get the aborted exception, use {@link #getExceptionIfAny()}.
	 * 
	 * @return The result of the task execution.
	 * @throws InnerTaskExecutionException
	 *             If the task execution failed.
	 */
	@RMISerialize
	@RMICacheResult
	public R getResult() throws InnerTaskExecutionException;

	/**
	 * Gets the exception that was thrown by the task execution if any, or the
	 * {@linkplain TaskContext#abortExecution(Throwable) aborted exception}.
	 * <p>
	 * If the task execution finished successfully, this method will return <code>null</code>.
	 * 
	 * @return The exception that was thrown by the task.
	 */
	@RMISerialize
	@RMICacheResult
	public Throwable getExceptionIfAny();
}