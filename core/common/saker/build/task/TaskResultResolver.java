package saker.build.task;

import saker.build.task.exception.TaskExecutionException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Container class for retrieving results of an executed task based on its task identifier.
 * <p>
 * Actual workings of this interface is implementation dependent.
 * <p>
 * This interface may be implemented by clients.
 *
 * @see TaskContext
 * @see TaskResultCollection
 */
public interface TaskResultResolver {
	/**
	 * Gets the task result for the given task identifier.
	 * <p>
	 * Any additional behaviour is implementation dependent.
	 * 
	 * @param taskid
	 *            The task identifier to retrieve the results for.
	 * @return The result of the task execution. Might be <code>null</code> if the task was not found, or it returned
	 *             <code>null</code> as a result.
	 * @throws TaskExecutionException
	 *             In case of any exceptions related to the task execution.
	 * @throws IllegalArgumentException
	 *             Optional exception, if a task result was not found for the given identifier.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	@RMISerialize
	public Object getTaskResult(@RMISerialize TaskIdentifier taskid)
			throws TaskExecutionException, IllegalArgumentException, NullPointerException;

	/**
	 * Gets a task result dependency handle for the given task identifier.
	 * <p>
	 * The retrieval of the handle is implementation dependent. The {@link TaskResultDependencyHandle#get()} method may
	 * return {@linkplain StructuredTaskResult structured task results}.
	 * 
	 * @param taskid
	 *            The task identifier
	 * @return The result dependency handle for the given task identifier.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             Optional exception, if a task result was not found for the given identifier.
	 */
	public default TaskResultDependencyHandle getTaskResultDependencyHandle(@RMISerialize TaskIdentifier taskid)
			throws NullPointerException, IllegalArgumentException {
		return TaskResultDependencyHandle.create(getTaskResult(taskid));
	}
}
