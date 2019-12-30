package saker.build.task;

import java.util.List;

import saker.build.task.TaskExecutionResult.TaskDependencies;
import saker.build.task.identifier.TaskIdentifier;

public interface TaskResultHolder<R> {
	public TaskIdentifier getTaskIdentifier();

	/**
	 * Gets the returned result of the task from {@link Task#run(TaskContext)}.
	 * <p>
	 * Will be <code>null</code> if there are any exceptions.
	 * 
	 * @return The returned object.
	 */
	public R getOutput();

	/**
	 * Gets the reported exceptions using {@link TaskContext#abortExecution(Throwable)}.
	 * 
	 * @return The exceptions. May be <code>null</code> or empty.
	 */
	public List<? extends Throwable> getAbortExceptions();

	/**
	 * Gets the exception that was thrown by the task from {@link Task#run(TaskContext)}.
	 * 
	 * @return The exception or <code>null</code> if none.
	 */
	public Throwable getFailCauseException();

	public TaskDependencies getDependencies();
}
