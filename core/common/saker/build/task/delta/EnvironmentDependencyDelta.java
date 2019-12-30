package saker.build.task.delta;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Build delta representing an {@link EnvironmentProperty} change.
 * <p>
 * The current value can be retrieved using
 * {@link SakerEnvironment#getEnvironmentPropertyCurrentValue(EnvironmentProperty)}.
 * 
 * @param <T>
 *            The type of the property.
 * @see TaskContext#reportEnvironmentDependency
 * @see DeltaType#ENVIRONMENT_PROPERTY_CHANGED
 */
public interface EnvironmentDependencyDelta<T> extends BuildDelta {
	/**
	 * Gets the property which has changed.
	 * 
	 * @return The property.
	 */
	@RMISerialize
	@RMICacheResult
	public EnvironmentProperty<T> getProperty();
}
