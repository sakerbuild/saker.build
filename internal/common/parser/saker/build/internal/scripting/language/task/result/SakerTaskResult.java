package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.StructuredTaskResult;

public interface SakerTaskResult extends Externalizable, StructuredTaskResult {
	public static final long serialVersionUID = 1L;

	public Object get(TaskResultResolver results);

	/**
	 * Gets a dependency handle that is associated with the object returned by {@link #get(TaskResultResolver)}.
	 */
	public default TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		return TaskResultDependencyHandle.create(get(results));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException;

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;

}
