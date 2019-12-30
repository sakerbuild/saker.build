package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.NavigableMap;

import saker.apiextract.api.PublicApi;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple immutable {@link BuildTargetTaskResult} backed by an underlying map.
 */
@PublicApi
public class SimpleBuildTargetTaskResult implements BuildTargetTaskResult, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Immutable map of result names mapped to their task identifiers.
	 */
	protected NavigableMap<String, TaskIdentifier> taskNamesToIds;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleBuildTargetTaskResult() {
	}

	/**
	 * Creates a new instance with the given values.
	 * <p>
	 * The argument is copied, so modifications do not propagate back to this instance.
	 * 
	 * @param taskNamesToIds
	 *            The result names mapped to their task identifiers.
	 */
	public SimpleBuildTargetTaskResult(NavigableMap<String, TaskIdentifier> taskNamesToIds) {
		this.taskNamesToIds = ImmutableUtils.makeImmutableNavigableMap(taskNamesToIds);
	}

	@Override
	public Map<String, TaskIdentifier> getTaskResultIdentifiers() {
		return taskNamesToIds;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, taskNamesToIds);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskNamesToIds = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskNamesToIds == null) ? 0 : taskNamesToIds.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleBuildTargetTaskResult other = (SimpleBuildTargetTaskResult) obj;
		if (!ObjectUtils.mapOrderedEquals(this.taskNamesToIds, other.taskNamesToIds)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskNamesToIds != null ? "taskNamesToIds=" + taskNamesToIds : "")
				+ "]";
	}

}
