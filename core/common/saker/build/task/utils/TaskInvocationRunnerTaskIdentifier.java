package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

final class TaskInvocationRunnerTaskIdentifier<T> implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	TaskFactory<T> taskFactory;
	NavigableMap<String, TaskIdentifier> parametersNameTaskIds;
	transient TaskName taskName;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationRunnerTaskIdentifier() {
	}

	public TaskInvocationRunnerTaskIdentifier(TaskName taskName, TaskFactory<T> taskFactory,
			NavigableMap<String, TaskIdentifier> parametersNameTaskIds) throws NullPointerException {
		this.taskName = taskName;
		this.taskFactory = taskFactory;
		this.parametersNameTaskIds = parametersNameTaskIds;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskFactory != null ? "taskFactory=" + taskFactory + ", " : "")
				+ (parametersNameTaskIds != null ? "parametersNameTaskIds=" + parametersNameTaskIds : "") + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parametersNameTaskIds == null) ? 0 : parametersNameTaskIds.hashCode());
		result = prime * result + ((taskFactory == null) ? 0 : taskFactory.hashCode());
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
		TaskInvocationRunnerTaskIdentifier<?> other = (TaskInvocationRunnerTaskIdentifier<?>) obj;
		if (parametersNameTaskIds == null) {
			if (other.parametersNameTaskIds != null)
				return false;
		} else if (!parametersNameTaskIds.equals(other.parametersNameTaskIds))
			return false;
		if (taskFactory == null) {
			if (other.taskFactory != null)
				return false;
		} else if (!taskFactory.equals(other.taskFactory))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskName);
		out.writeObject(taskFactory);

		SerialUtils.writeExternalMap(out, parametersNameTaskIds);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskName = (TaskName) in.readObject();
		taskFactory = (TaskFactory<T>) in.readObject();

		parametersNameTaskIds = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}
}
