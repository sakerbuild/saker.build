package saker.build.task.event;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.identifier.TaskIdentifier;

public class TaskIdTaskEvent implements TaskExecutionEvent, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskExecutionEventKind kind;
	private TaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskIdTaskEvent() {
	}

	public TaskIdTaskEvent(TaskExecutionEventKind kind, TaskIdentifier taskId) {
		this.kind = kind;
		this.taskId = taskId;
	}

	@Override
	public TaskExecutionEventKind getKind() {
		return kind;
	}

	public TaskIdentifier getTaskId() {
		return taskId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(kind);
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		kind = (TaskExecutionEventKind) in.readObject();
		taskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((kind == null) ? 0 : kind.hashCode());
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
		TaskIdTaskEvent other = (TaskIdTaskEvent) obj;
		if (kind != other.kind)
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
		return getClass().getSimpleName() + "[" + (kind != null ? "kind=" + kind + ", " : "")
				+ (taskId != null ? "taskId=" + taskId : "") + "]";
	}

}
