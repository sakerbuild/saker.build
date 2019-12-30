package saker.build.task;

import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Handle to a task result that allows modifying the applied dependency to the underlying task.
 * <p>
 * This interface provides access to a task result that was associated with it during construction time. The interface
 * optionally supports modifying the applied dependency to the associated task. Specifying the dependency via
 * {@link #setTaskOutputChangeDetector(TaskOutputChangeDetector)} may not always have an effect, and it is
 * implementation dependent.
 * <p>
 * The interface doesn't define the manner of how the task result is retrieved, and it is implementation dependent based
 * on the context.
 * <p>
 * This interface mainly exists to make fine graining dependency application possible using {@link TaskResultResolver}
 * and when working with {@linkplain StructuredTaskResult structured task results}. <br>
 * Working with objects that have their values backed by task results may occurr when the build task executions are
 * already finished. In these cases, a {@link TaskResultResolver} instance can be used to retrieve task results for
 * given task identifiers. However, when working with the same data during task execution, the task dependencies may
 * need to be refined for proper incremental implementation. As the objects have a {@link TaskResultResolver} reference
 * instead of {@link TaskContext}, without the use of this interface the dependency reification would not be possible,
 * although it is necessary by the consumers.
 * <p>
 * By using this interface, the task result consumers can have a common interface to work with task results that support
 * dependency reification and with those that don't. If the {@link TaskOutputChangeDetector} cannot be set, the
 * implementations will silently ignore.
 * <p>
 * This interface may be implemented by clients.
 * 
 * @see TaskResultResolver#getTaskResultDependencyHandle(TaskIdentifier)
 */
public interface TaskResultDependencyHandle {
	/**
	 * Gets the task result for the associated task.
	 * <p>
	 * The behaviour of this method is implementation dependent, it may wait for the associated task to finish.
	 * <p>
	 * If {@link #setTaskOutputChangeDetector(TaskOutputChangeDetector)} is not called on an instance after retrieving
	 * the result, an implementation dependent dependency may be installed. (Most commonly an equivalent dependency to
	 * {@link CommonTaskOutputChangeDetector#ALWAYS}.)
	 * <p>
	 * The result of this call may or may not be an instance of {@link StructuredTaskResult} based on the associated
	 * context the {@link TaskResultDependencyHandle} was retrieved from.
	 * 
	 * @return The task result.
	 * @throws RuntimeException
	 *             If the task result retrieval failed. It may be because the task execution failed, waiting for the
	 *             task failed, or any other implementation dependent reasons.
	 */
	@RMISerialize
	public Object get() throws RuntimeException;

	/**
	 * Sets the output change detector for the associated task dependency.
	 * <p>
	 * Works the same way as {@link TaskDependencyFuture#setTaskOutputChangeDetector(TaskOutputChangeDetector)}.
	 * <p>
	 * Implementations that don't support this method must silently ignore it. If they don't support it, and task
	 * dependencies make sense in the associated context, then a dependency equivalent to
	 * {@link CommonTaskOutputChangeDetector#ALWAYS} must be installed.
	 * 
	 * @param outputchangedetector
	 *            The output change detector.
	 * @throws IllegalStateException
	 *             If the method is called at an inaproppriate time.
	 * @throws NullPointerException
	 *             If the change detector is <code>null</code>.
	 */
	public default void setTaskOutputChangeDetector(@RMISerialize TaskOutputChangeDetector outputchangedetector)
			throws IllegalStateException, NullPointerException {
		//silently ignore by default
	}

	/**
	 * Clones the dependency handle, returning a clean one.
	 * <p>
	 * The returned dependency handle is semantically the same as if it was newly retrieved the same method as
	 * <code>this</code> handle.
	 * 
	 * @return The cloned dependency handle.
	 */
	public TaskResultDependencyHandle clone();

	/**
	 * Creates a simple {@link TaskResultDependencyHandle} that returns the argument object.
	 * <p>
	 * The returned dependency handle has no dependency support, only a simple wrapper for the specified object.
	 * 
	 * @param resultobject
	 *            The backing object.
	 * @return The created dependency handle.
	 * @see #get()
	 */
	public static TaskResultDependencyHandle create(Object resultobject) {
		return new RetrievedTaskResultDependencyHandle(resultobject);
	}
}
