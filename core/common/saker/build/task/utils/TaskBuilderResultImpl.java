package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

//no need to be public
final class TaskBuilderResultImpl<R> implements TaskBuilderResult<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskIdentifier taskId;
	private TaskFactory<R> taskFactory;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskBuilderResultImpl() {
	}

	TaskBuilderResultImpl(TaskIdentifier taskid, TaskFactory<R> taskfactory) {
		this.taskId = taskid;
		this.taskFactory = taskfactory;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	@Override
	public TaskFactory<R> getTaskFactory() {
		return taskFactory;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
		out.writeObject(taskFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = SerialUtils.readExternalObject(in);
		taskFactory = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskFactory == null) ? 0 : taskFactory.hashCode());
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
		TaskBuilderResultImpl<?> other = (TaskBuilderResultImpl<?>) obj;
		if (taskFactory == null) {
			if (other.taskFactory != null)
				return false;
		} else if (!taskFactory.equals(other.taskFactory))
			return false;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskId != null ? "taskId=" + taskId + ", " : "")
				+ (taskFactory != null ? "taskFactory=" + taskFactory : "") + "]";
	}

}