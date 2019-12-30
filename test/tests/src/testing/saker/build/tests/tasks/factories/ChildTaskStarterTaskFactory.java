package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class ChildTaskStarterTaskFactory implements TaskFactory<Void>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Map<TaskIdentifier, TaskFactory<?>> namedChildTaskValues = new HashMap<>();

	public ChildTaskStarterTaskFactory() {
	}

	public ChildTaskStarterTaskFactory(Map<TaskIdentifier, TaskFactory<?>> namedChildTaskValues) {
		this.namedChildTaskValues = namedChildTaskValues;
	}

	public ChildTaskStarterTaskFactory add(TaskIdentifier taskid, TaskFactory<?> factory) {
		namedChildTaskValues.put(taskid, factory);
		return this;
	}

	public ChildTaskStarterTaskFactory remove(TaskIdentifier taskid) {
		namedChildTaskValues.remove(taskid);
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, namedChildTaskValues);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		namedChildTaskValues = SerialUtils.readExternalMap(new HashMap<>(), in);
	}

	@Override
	public Task<Void> createTask(ExecutionContext context) {
		return new Task<Void>() {
			@Override
			public Void run(TaskContext context) {
				for (Entry<? extends TaskIdentifier, ? extends TaskFactory<?>> entry : namedChildTaskValues
						.entrySet()) {
					context.getTaskUtilities().startTaskFuture(entry.getKey(), entry.getValue());
				}
				return null;
			}
		};
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((namedChildTaskValues == null) ? 0 : namedChildTaskValues.hashCode());
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
		ChildTaskStarterTaskFactory other = (ChildTaskStarterTaskFactory) obj;
		if (namedChildTaskValues == null) {
			if (other.namedChildTaskValues != null)
				return false;
		} else if (!namedChildTaskValues.equals(other.namedChildTaskValues))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ChildTaskCreatorTaskFactory ["
				+ (namedChildTaskValues != null ? "namedChildTaskValues=" + namedChildTaskValues : "") + "]";
	}

}