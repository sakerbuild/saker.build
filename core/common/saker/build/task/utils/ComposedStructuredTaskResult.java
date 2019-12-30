package saker.build.task.utils;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultResolver;

/**
 * {@link StructuredTaskResult} interface representing a result that may contain other intermediate structured task
 * results.
 * <p>
 * The {@link #getIntermediateTaskResult(TaskResultResolver)} can be used to retrieve intermediate results. The
 * intermediate results must return semantically the same object from their
 * {@link StructuredTaskResult#toResult(TaskResultResolver)} function as <code>this</code> instance.
 * <p>
 * This interface is useful when the result of a task depends on other possibly structured task results. In cases like
 * this, the consumer of the composed structured task result may optimize its behaviour and dependency management based
 * on the intermediate results.
 * <p>
 * The interface should be implemented by clients to be used.
 */
@PublicApi
public interface ComposedStructuredTaskResult {
	/**
	 * Gets the intermediate task result that this task result is composed of.
	 * <p>
	 * The intermediate results has the same final result semantics as <code>this</code> instance. Its
	 * {@link StructuredTaskResult#toResult(TaskResultResolver)} and related methods must provide access to the
	 * semantically same object as <code>this</code>.
	 * <p>
	 * <b>Note for implementations</b>: This method must never return an object semantically same as <code>this</code>.
	 * 
	 * @param results
	 *            The results to resolve the task identifiers against.
	 * @return The intermediate task result. Never semantically same as <code>this</code>. Never <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws RuntimeException
	 *             In cases when the result cannot be computed properly. (E.g. task execution failure)
	 */
	public StructuredTaskResult getIntermediateTaskResult(TaskResultResolver results)
			throws NullPointerException, RuntimeException;
}
