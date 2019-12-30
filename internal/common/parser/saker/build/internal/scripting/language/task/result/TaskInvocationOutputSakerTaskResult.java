package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;

public class TaskInvocationOutputSakerTaskResult implements SakerTaskResult, StructuredObjectTaskResult {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationOutputSakerTaskResult() {
	}

	public TaskInvocationOutputSakerTaskResult(TaskIdentifier futuretaskid) {
		this.taskId = futuretaskid;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	@Override
	public Object get(TaskResultResolver results) {
		return this;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		return StructuredTaskResult.getActualTaskResult(taskId, results);
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		return StructuredTaskResult.getActualTaskResultDependencyHandle(taskId, results);
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		return handleforthis;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
		TaskInvocationOutputSakerTaskResult other = (TaskInvocationOutputSakerTaskResult) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + taskId + "]";
	}

}
