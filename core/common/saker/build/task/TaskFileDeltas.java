package saker.build.task;

import java.util.Set;

import saker.build.task.delta.DeltaType;
import saker.build.task.delta.FileChangeDelta;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIHashSetWrapper;

/**
 * Container for holding and handling file related task deltas.
 * 
 * @see FileChangeDelta
 * @see TaskContext#getFileDeltas(DeltaType)
 */
public interface TaskFileDeltas {
	/**
	 * Gets the file deltas contained in this instance.
	 * 
	 * @return An unmodifiable set of file deltas.
	 */
	@RMICacheResult
	@RMIWrap(RMIHashSetWrapper.class)
	public Set<? extends FileChangeDelta> getFileDeltas();

	/**
	 * Gets the file deltas for the given tag.
	 * <p>
	 * {@link FileChangeDelta#getTag()} will be equal to the parameter tag for every delta in the result.
	 * 
	 * @param tag
	 *            The tag used when the file dependency was reported.
	 * @return An unmodifiable set of file deltas with the given tag.
	 */
	@RMIWrap(RMIHashSetWrapper.class)
	public Set<? extends FileChangeDelta> getFileDeltasWithTag(@RMISerialize Object tag);

	/**
	 * Gets a file delta for the given tag if exists.
	 * <p>
	 * {@link FileChangeDelta#getTag()} will be equal to the parameter tag for the result.
	 * 
	 * @param tag
	 *            The tag used when the file dependency was reported.
	 * @return A delta for the given tag, or <code>null</code> if none exists for it.
	 */
	public default FileChangeDelta getAnyFileDeltaWithTag(@RMISerialize Object tag) {
		Set<? extends FileChangeDelta> deltas = getFileDeltasWithTag(tag);
		if (deltas.isEmpty()) {
			return null;
		}
		return deltas.iterator().next();
	}

	/**
	 * Checks if there are any deltas for the given tag.
	 * 
	 * @param tag
	 *            The tag used when the file dependency was reported.
	 * @return <code>true</code> if there is at least one delta for the given tag.
	 */
	public default boolean hasFileDeltaWithTag(@RMISerialize Object tag) {
		return !getFileDeltasWithTag(tag).isEmpty();
	}

	/**
	 * Checks if <code>this</code> instance contains any deltas.
	 * 
	 * @return <code>true</code> if this instance has no deltas.
	 */
	@RMICacheResult
	public default boolean isEmpty() {
		return getFileDeltas().isEmpty();
	}
}