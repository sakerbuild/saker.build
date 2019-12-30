package saker.build.runtime.execution;

import java.io.Externalizable;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Specifies a property which can be derived from the build execution context.
 * <p>
 * Properties are used to determine various aspects of the current build execution context. They depend on the current
 * build configuration and preferably unrelated to the current build environment. {@link EnvironmentProperty} should be
 * used for build environment related properties.
 * <p>
 * Implementations are required to override {@link #equals(Object)} and {@link #hashCode()}. Property implementations
 * acts as a key to determine what they compute from the given build execution context.
 * <p>
 * Task implementations can use these classes to depend on various aspects of the build execution context.
 * <p>
 * It is not assumed that execution properties stay the same between consecutive build executions, but it is assumed
 * that they stay the same between consecutive computations on the same execution context in the same build execution.
 * <p>
 * It is strongly recommended that implementations and the calculated property values implement the
 * {@link Externalizable} interface.
 * <p>
 * Good examples for execution properties:
 * <ul>
 * <li>{@linkplain ExecutionContext#getUserParameters() Execution user parameters}.</li>
 * <li>The current build time. (See {@link ExecutionContext#getBuildTimeMillis()})</li>
 * </ul>
 * <p>
 * Bad examples for execution properties: <br>
 * Any good example for {@link EnvironmentProperty}. (See the examples in its documentation)
 * 
 * @param <T>
 *            The type of the returned property.
 * @see TaskContext#reportExecutionDependency(ExecutionProperty, Object)
 * @see ExecutionContext#getExecutionPropertyCurrentValue(ExecutionProperty)
 * @see TaskExecutionUtilities#getReportExecutionDependency(ExecutionProperty)
 */
public interface ExecutionProperty<T> {
	/**
	 * Computes the value of this execution property.
	 * <p>
	 * It is strongly recommended for the returned value to implement {@link #equals(Object)} in order for proper
	 * incremental functionality.
	 * 
	 * @param executioncontext
	 *            The execution context to use for the computation.
	 * @return The computed value.
	 * @throws Exception
	 *             If any exception happens during the computation of the property.
	 */
	@RMISerialize
	public T getCurrentValue(ExecutionContext executioncontext) throws Exception;

	/**
	 * Determines if this property will compute the same values as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	public int hashCode();
}
