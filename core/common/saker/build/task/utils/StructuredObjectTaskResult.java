package saker.build.task.utils;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;

/**
 * {@link StructuredTaskResult} for a single object.
 * <p>
 * The result object is simply identified by the identifier of the corresponding task.
 * 
 * @see SimpleStructuredObjectTaskResult
 */
@PublicApi
public interface StructuredObjectTaskResult extends StructuredTaskResult {
	/**
	 * Gets the task identifier of the task which produces the result.
	 * <p>
	 * The result of the associated task may still be a structured task result.
	 * 
	 * @return The task identifier.
	 */
	public TaskIdentifier getTaskIdentifier();

	@Override
	public default Object toResult(TaskResultResolver results) {
		return StructuredTaskResult.getActualTaskResult(getTaskIdentifier(), results);
	}

	@Override
	default TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results)
			throws NullPointerException {
		return StructuredTaskResult.getActualTaskResultDependencyHandle(getTaskIdentifier(), results);
	}
}
