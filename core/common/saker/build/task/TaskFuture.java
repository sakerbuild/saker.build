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
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskResultWaitingFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Represents a handle to a specified task.
 * <p>
 * This interface can be used by tasks to get the result of an input task.
 * <p>
 * By querying the result an input dependency will be recorded on the subject task. (See {@link #get()})
 * <p>
 * Instances of this interface can be reused and should be considered as a singleton for a given task identifier.
 * <p>
 * Methods of this class may throw (but not required in all ceses) {@link IllegalTaskOperationException} if it detects
 * that they are being called after the corresponding task execution has finished. References to task futures should not
 * be retained after the task execution is over.
 * <p>
 * Clients should not implement this interface.
 * 
 * @param <R>
 *            The return type of the task.
 */
public interface TaskFuture<R> {
	/**
	 * Retrieves the result of the associated task execution.
	 * <p>
	 * This call will wait for if necessary and report an input dependency on the subject task. The output change
	 * detector on the task will be {@link CommonTaskOutputChangeDetector#ALWAYS}.
	 * <p>
	 * Callers should handle the possiblity of tasks returning {@link StructuredTaskResult} instances.
	 * <p>
	 * If this method throws a {@link TaskExecutionFailedException}, it is considered to be a valid return scenario. A
	 * dependency on the the associated task will be installed as if a return value was returned.
	 * <p>
	 * This call has the same effect as:
	 * 
	 * <pre>
	 * TaskDependencyFuture&lt;R&gt; res = getAsDependencyFuture();
	 * res.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.ALWAYS);
	 * return res.get();
	 * </pre>
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
	 * This method will not wait for the task to finish, and will throw an exception if it is not yet finished. An input
	 * dependency on the task will be reported with the output change detector of
	 * {@link CommonTaskOutputChangeDetector#ALWAYS}.
	 * <p>
	 * <b>Important:</b> To ensure incremental reproducibility, this method will validate if the caller is allowed to
	 * retrieve the finished result of the associated task. This is done by checking if the associated task have been
	 * waited for prior to this call, or by one of the ancestors of the caller task before starting the caller task.
	 * <p>
	 * Example 1: Task A wants to retrieve the finished result of task B. If task A calls {@link #getFinished()}, it
	 * will throw an exception, as it cannot be ensured that A is retrieving the result for a task that has been already
	 * finished. <br>
	 * Example 2: Task A wants to retrieve the finished result of task B. If task A calls {@link #get()} first, and
	 * later {@link #getFinished()}, then the second call will succeed, as task A already waited for the result of task
	 * B, therefore the finished result can be also retrieved. <br>
	 * Example 3: Task S waits for task B, and starts task A (in this order). Task A wants to retrieve the finished
	 * result of task B. In this case task A can call {@link #getFinished()} for task B, as it can be sure that task B
	 * is finished, because its parent has already waited for task B before starting task A.
	 * <p>
	 * In general, when retrieving the finished result of a task, make sure that the task (Example 2) or any of its
	 * ancestors (Example 3) have waited for the task itself. If this requirement is violated, an exception will be
	 * thrown.
	 * <p>
	 * The build system detects this requirement when handling incremental builds. Meaning, that if retrieving a
	 * finished result for a task is no longer possible, the caller will be rerun.
	 * <p>
	 * Callers should handle the possiblity of tasks returning {@link StructuredTaskResult} instances.
	 * <p>
	 * Tasks which {@linkplain TaskFactory#getRequestedComputationTokenCount() request computation tokens} are allowed
	 * to call this method in order to add a dependency on the associated task.
	 * <p>
	 * This call has the same effect as:
	 * 
	 * <pre>
	 * TaskDependencyFuture&lt;R&gt; res = getAsDependencyFuture();
	 * res.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.ALWAYS);
	 * return res.getFinished();
	 * </pre>
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
	 * Gets a dependency future handle to the subject task.
	 * <p>
	 * The handle can be used to fine-grain the dependency reported for the subject task.
	 * <p>
	 * The returned handle is specific for the caller, it can be used to report and configure a single dependency
	 * towards the subject task.
	 * <p>
	 * This method can be called multiple times, different instances will be returned, but the results should not be
	 * exchanged by different parts of client code.
	 * 
	 * @return The dependency future handle.
	 */
	public TaskDependencyFuture<R> asDependencyFuture();

	/**
	 * Gets the task identifier of the subject class.
	 * 
	 * @return The task identifier.
	 */
	@RMICacheResult
	public TaskIdentifier getTaskIdentifier();

	/**
	 * Gets the modification stamp of the subject task.
	 * <p>
	 * Modification stamps are unique key objects which specify the time when the subject task was last run. Stamps
	 * should be compared by {@linkplain Object#equals(Object) equality} to check if the task has been rerun. If two
	 * stamps for a given task does not equal to each other, then the caller can be sure that the associated task was
	 * rerun at least once between the previous and current execution of the caller task.
	 * <p>
	 * This method can only be called if the task has already finished. This call doesn't add an input dependency on the
	 * subject task. It is recommended to call {@link #get()} before calling this function, or ensure that the task has
	 * finished in some other way.
	 * <p>
	 * When calling this method, the same rules apply as in {@link #getFinished()}.
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
	 * Gets the task context which was used to retrieve this future.
	 * 
	 * @return The task context.
	 * @see TaskContext#getTaskFuture(TaskIdentifier)
	 */
	@RMICacheResult
	public TaskContext getTaskContext();
}
