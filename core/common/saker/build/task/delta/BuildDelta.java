package saker.build.task.delta;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Common superinterface for build task deltas.
 * <p>
 * Subclasses should implement {@link #equals(Object)} and {@link #hashCode()}.
 *
 * @see DeltaType
 */
public interface BuildDelta {
	/**
	 * Gets the type of this build delta.
	 * 
	 * @return The type.
	 */
	@RMICacheResult
	public DeltaType getType();

	@Override
	public boolean equals(Object obj);

	@Override
	public int hashCode();
}
