package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Simple {@link StructuredObjectTaskResult} data class implementation.
 */
@PublicApi
public class SimpleStructuredObjectTaskResult implements StructuredObjectTaskResult, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskIdentifier taskIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleStructuredObjectTaskResult() {
	}

	/**
	 * Creates a new instance holding the specified task identifier.
	 * 
	 * @param taskIdentifier
	 *            The task identifier.
	 */
	public SimpleStructuredObjectTaskResult(TaskIdentifier taskIdentifier) {
		this.taskIdentifier = taskIdentifier;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		return StructuredTaskResult.getActualTaskResult(taskIdentifier, results);
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		return StructuredTaskResult.getActualTaskResultDependencyHandle(taskIdentifier, results);
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskIdentifier;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskIdentifier = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskIdentifier == null) ? 0 : taskIdentifier.hashCode());
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
		SimpleStructuredObjectTaskResult other = (SimpleStructuredObjectTaskResult) obj;
		if (taskIdentifier == null) {
			if (other.taskIdentifier != null)
				return false;
		} else if (!taskIdentifier.equals(other.taskIdentifier))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + taskIdentifier + "]";
	}

}
