package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SequentialChildTaskStarterTaskFactory implements TaskFactory<Void>, Externalizable {
	private static final long serialVersionUID = 1L;

	private List<Map.Entry<TaskIdentifier, TaskFactory<?>>> tasks = new ArrayList<>();

	public SequentialChildTaskStarterTaskFactory() {
	}

	public SequentialChildTaskStarterTaskFactory add(TaskIdentifier taskid, TaskFactory<?> factory) {
		tasks.add(ImmutableUtils.makeImmutableMapEntry(taskid, factory));
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, tasks);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		tasks = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public Task<Void> createTask(ExecutionContext context) {
		return new Task<Void>() {
			@Override
			public Void run(TaskContext context) {
				for (Entry<? extends TaskIdentifier, ? extends TaskFactory<?>> entry : tasks) {
					context.getTaskUtilities().runTaskResult(entry.getKey(), entry.getValue());
				}
				return null;
			}
		};
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((tasks == null) ? 0 : tasks.hashCode());
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
		SequentialChildTaskStarterTaskFactory other = (SequentialChildTaskStarterTaskFactory) obj;
		if (tasks == null) {
			if (other.tasks != null)
				return false;
		} else if (!tasks.equals(other.tasks))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SequentialChildTaskStarterTaskFactory [" + tasks + "]";
	}

}