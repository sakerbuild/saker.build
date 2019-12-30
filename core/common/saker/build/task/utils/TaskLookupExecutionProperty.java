package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;

/**
 * {@link ExecutionProperty} implementation for looking up a {@link TaskFactory} instance based on a {@link TaskName}
 * and repository identifier.
 */
@PublicApi
public class TaskLookupExecutionProperty implements ExecutionProperty<TaskFactory<?>>, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskName taskName;
	private String repository;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskLookupExecutionProperty() {
	}

	/**
	 * Creates a new instance for the given task name and repository identifier.
	 * 
	 * @param taskName
	 *            The task name to look up.
	 * @param repository
	 *            The repository identifier or <code>null</code> if any repository can be used to look up the task.
	 * @throws NullPointerException
	 *             If task name is <code>null</code>.
	 */
	public TaskLookupExecutionProperty(TaskName taskName, String repository) throws NullPointerException {
		Objects.requireNonNull(taskName, "task name");
		this.taskName = taskName;
		this.repository = repository;
	}

	/**
	 * Gets the task name to look up.
	 * 
	 * @return The task name.
	 */
	public TaskName getTaskName() {
		return taskName;
	}

	/**
	 * Gets the repository identifier to look up the task in.
	 * 
	 * @return The repository identifier or <code>null</code> if any repository can be used.
	 */
	public String getRepository() {
		return repository;
	}

	@Override
	public TaskFactory<?> getCurrentValue(ExecutionContext executioncontext) {
		return TaskUtils.createTask(executioncontext, taskName, repository);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskName);
		out.writeObject(repository);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskName = (TaskName) in.readObject();
		repository = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((repository == null) ? 0 : repository.hashCode());
		result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
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
		TaskLookupExecutionProperty other = (TaskLookupExecutionProperty) obj;
		if (repository == null) {
			if (other.repository != null)
				return false;
		} else if (!repository.equals(other.repository))
			return false;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskName != null ? "taskName=" + taskName + ", " : "")
				+ (repository != null ? "repository=" + repository : "") + "]";
	}

}
