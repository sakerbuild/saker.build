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

import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskResultWaitingFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Represents a handle to a specified task to query the result from.
 * <p>
 * This interface allows fine-grained dependency reporting for a given task. It should not be reused and is considered
 * to be private to the user. The difference between this and {@link TaskFuture} is that instances of this should not be
 * reused by different parts of client code.
 * <p>
 * The method {@link #setTaskOutputChangeDetector(TaskOutputChangeDetector)} can be used to specify a custom output
 * change detector for the input task.
 * <p>
 * Instances of this interface should be used by only a single thread. Method calls should not overlap.
 * <p>
 * Instances of this interface is usually obtained from {@link TaskFuture#asDependencyFuture()} or
 * {@link TaskContext#getTaskDependencyFuture(TaskIdentifier)}.
 * <p>
 * Methods of this class may throw (but not required in all cases) {@link IllegalTaskOperationException} if it detects
 * that they are being called after the caller task execution has finished. References to task dependency futures should
 * not be retained after the task execution is over.
 * <p>
 * Clients should not implement this interface.
 * 
 * @param <R>
 *            The return type of the task.
 */
public interface TaskDependencyFuture<R> {
	/**
	 * Retrieves the result of the associated task execution.
	 * <p>
	 * This call will wait for the task if necessary and report an input dependency on it.
	 * <p>
	 * The output change detector for the reported task dependency will be {@link CommonTaskOutputChangeDetector#ALWAYS}
	 * by default. It can be replaced if the client calls {@link #setTaskOutputChangeDetector(TaskOutputChangeDetector)}
	 * after or before calling this function.
	 * <p>
	 * If this method throws a {@link TaskExecutionFailedException}, it is considered to be a valid return scenario. A
	 * dependency on the the associated task will be installed as if a return value was returned.
	 * <p>
	 * Callers should handle the possiblity of tasks returning {@link StructuredTaskResult} instances.
	 * 
	 * @return The result of the subject task.
	 * @throws TaskResultWaitingFailedException
	 *             If the waiting failed. See exception subclasses for possible scenarios.
	 * @throws TaskExecutionFailedException
	 *             If the subject task execution failed, this exception is thrown
	 * @throws IllegalTaskOperationException
	 *             If the current task is not allowed to wait for the subject task. See exception message for more
	 *             information.
	 */
	@RMISerialize
	@RMICacheResult
	public R get() throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException;

	/**
	 * Retrieves the result of the associated task, given that it is already finished.
	 * <p>
	 * The output change detector for the reported task dependency will be {@link CommonTaskOutputChangeDetector#ALWAYS}
	 * by default. It can be replaced if the client calls {@link #setTaskOutputChangeDetector(TaskOutputChangeDetector)}
	 * after or before calling this function.
	 * <p>
	 * <b>Important:</b> See {@link TaskFuture#getFinished()} for the requirements for calling this method.
	 * <p>
	 * Callers should handle the possiblity of tasks returning {@link StructuredTaskResult} instances.
	 * 
	 * @return The result of the subject task.
	 * @throws TaskExecutionFailedException
	 *             If the subject task execution failed, this exception is thrown
	 * @throws IllegalTaskOperationException
	 *             If the current task is not allowed to retrieve the result for the subject task, or the subject task
	 *             hasn't finished yet.
	 */
	@RMISerialize
	@RMICacheResult
	public R getFinished() throws TaskExecutionFailedException, IllegalTaskOperationException;

	/**
	 * Sets the output change detector for the subject task dependency.
	 * <p>
	 * If the task dependency was not yet reported this task, this method reports it. The output change detector will be
	 * applied for this particular dependency on the task.
	 * <p>
	 * If the build runtime detects a change in the task output, the caller task will be rerun with an appropriate
	 * delta.
	 * <p>
	 * This method can be called more than once to add multiple change detectors.
	 * 
	 * @param outputchangedetector
	 *            The output change detector.
	 * @throws IllegalStateException
	 *             If the task has not yet finished.
	 * @throws NullPointerException
	 *             If the change detector is <code>null</code>.
	 */
	public void setTaskOutputChangeDetector(@RMISerialize TaskOutputChangeDetector outputchangedetector)
			throws IllegalStateException, NullPointerException;

	/**
	 * Gets the modification stamp of the subject task.
	 * <p>
	 * See {@link TaskFuture#getModificationStamp()} for documentation.
	 * 
	 * @return The current modification stamp for the subject task.
	 * @throws IllegalTaskOperationException
	 *             If the current task is not allowed to retrieve the result for the subject task, or the subject task
	 *             hasn't finished yet.
	 */
	@RMISerialize
	@RMICacheResult
	public Object getModificationStamp() throws IllegalTaskOperationException;

	/**
	 * Gets the task identifier of the subject task.
	 * 
	 * @return The task identifier.
	 */
	@RMICacheResult
	public TaskIdentifier getTaskIdentifier();

	/**
	 * Gets the task context which was used to retrieve this dependency future.
	 * 
	 * @return The task context.
	 * @see TaskContext#getTaskDependencyFuture(TaskIdentifier)
	 * @see TaskFuture#asDependencyFuture()
	 */
	@RMICacheResult
	public TaskContext getTaskContext();

	/**
	 * Clones the dependency future object, returning a clean one.
	 * <p>
	 * The returned dependency future is semantically the same as if a new one was directly retrieved using
	 * {@link TaskContext#getTaskDependencyFuture(TaskIdentifier)}. (Or {@link TaskFuture#asDependencyFuture()}.)
	 * 
	 * @return The cloned dependency future.
	 */
	public TaskDependencyFuture<R> clone();
}
