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
	 * Gets the result of the task execution, or throws an exception if failed.
	 * <p>
	 * This method will return the object that was returned from the {@link Task#run(TaskContext)} method of the inner
	 * task. If the call threw an exception before finishing properly, this method will throw an
	 * {@link InnerTaskExecutionException} with its cause set to the thrown exception.
	 * 
	 * @return The result of the task execution.
	 * @throws InnerTaskExecutionException
	 *             If the task execution failed.
	 */
	@RMISerialize
	@RMICacheResult
	public R getResult() throws InnerTaskExecutionException;

	/**
	 * Gets the exception that was thrown by the task execution if any.
	 * <p>
	 * If the task execution finished successfully, this method will return <code>null</code>.
	 * 
	 * @return The exception that was thrown by the task.
	 */
	@RMISerialize
	@RMICacheResult
	public Throwable getExceptionIfAny();
}