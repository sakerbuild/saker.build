package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.task.identifier.TaskIdentifier;

public class StringTaskConcatTaskFactory implements TaskFactory<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String value;
	private TaskIdentifier dependentTaskId;

	public StringTaskConcatTaskFactory() {
	}

	public StringTaskConcatTaskFactory(String value, TaskFuture<String> dependentTask) {
		this(value, dependentTask.getTaskIdentifier());
	}

	public StringTaskConcatTaskFactory(String value, TaskIdentifier dependentTaskId) {
		this.value = value;
		this.dependentTaskId = dependentTaskId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dependentTaskId == null) ? 0 : dependentTaskId.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		StringTaskConcatTaskFactory other = (StringTaskConcatTaskFactory) obj;
		if (dependentTaskId == null) {
			if (other.dependentTaskId != null)
				return false;
		} else if (!dependentTaskId.equals(other.dependentTaskId))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(value);
		out.writeObject(dependentTaskId);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		value = in.readUTF();
		dependentTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public Task<String> createTask(ExecutionContext excontext) {
		return new Task<String>() {
			@Override
			public String run(TaskContext context) {
				String taskval = (String) context.getTaskResult(dependentTaskId);
				System.out.println("StringTaskConcatTaskFactory.createTask(...).new Task() {...}.run() " + taskval);
				return value + taskval;
			}
		};
	}

	@Override
	public String toString() {
		return "StringTaskConcatTaskFactory [" + (value != null ? "value=" + value + ", " : "")
				+ (dependentTaskId != null ? "dependentTaskId=" + dependentTaskId : "") + "]";
	}

}
