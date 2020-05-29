package saker.build.task.utils;

import java.io.Externalizable;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Interface holding the result of a task builder operation.
 * <p>
 * The interface provides access to the {@linkplain #getTaskIdentifier() task identifier} and
 * {@linkplain #getTaskFactory() task factory} of the built task. This pair can be used to start new tasks in the build
 * system.
 * <p>
 * This is an utility interface that serves to represent the pair of a task identifier and corresponding task factory.
 * It has no significance in the build system runtime, but is generally used by extensions/plugins for saker.build.
 * <p>
 * Use {@link #create(TaskIdentifier, TaskFactory)} to create a new instance.
 * 
 * @param <R>
 *            The result type of the task.
 * @see TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)
 * @since saker.build 0.8.12
 */
@PublicApi
public interface TaskBuilderResult<R> {
	/**
	 * Gets the task identifer.
	 * 
	 * @return The task id.
	 */
	public TaskIdentifier getTaskIdentifier();

	/**
	 * Gets the task factory.
	 * 
	 * @return The task factory.
	 */
	public TaskFactory<R> getTaskFactory();

	/**
	 * Creates a new instance.
	 * <p>
	 * The returned instance implements the {@link #hashCode()} and {@link #equals(Object)} contract in relation with
	 * other instances created by this method. The returned object also implements {@link Externalizable}.
	 * 
	 * @param <R>
	 *            The result type of the task.
	 * @param taskid
	 *            The task identifier.
	 * @param taskfactory
	 *            The task factory.
	 * @return The created instance.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <R> TaskBuilderResult<R> create(TaskIdentifier taskid, TaskFactory<R> taskfactory)
			throws NullPointerException {
		Objects.requireNonNull(taskid, "task id");
		Objects.requireNonNull(taskfactory, "task factory");
		return new TaskBuilderResultImpl<R>(taskid, taskfactory);
	}
}
