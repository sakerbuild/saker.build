package saker.build.task.delta;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Build delta representing an {@link ExecutionProperty} change.
 * <p>
 * The current value can be retrieved using
 * {@link ExecutionContext#getExecutionPropertyCurrentValue(ExecutionProperty)}.
 * 
 * @param <T>
 *            The type of the property.
 * @see TaskContext#reportExecutionDependency
 * @see DeltaType#EXECUTION_PROPERTY_CHANGED
 */
public interface ExecutionDependencyDelta<T> extends BuildDelta {
	/**
	 * Gets the property which has changed.
	 * 
	 * @return The property.
	 */
	@RMISerialize
	@RMICacheResult
	public ExecutionProperty<T> getProperty();
}
