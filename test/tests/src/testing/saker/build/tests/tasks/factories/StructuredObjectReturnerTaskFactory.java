package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;

public class StructuredObjectReturnerTaskFactory
		implements TaskFactory<StructuredTaskResult>, Task<StructuredTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public StructuredObjectReturnerTaskFactory() {
	}

	public StructuredObjectReturnerTaskFactory(TaskIdentifier taskId) {
		this.taskId = taskId;
	}

	@Override
	public Task<? extends StructuredTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public StructuredTaskResult run(TaskContext taskcontext) throws Exception {
		return new SimpleStructuredObjectTaskResult(taskId);
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
		StructuredObjectReturnerTaskFactory other = (StructuredObjectReturnerTaskFactory) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "StructuredObjectReturnerTaskFactory[" + (taskId != null ? "taskId=" + taskId : "") + "]";
	}
}
