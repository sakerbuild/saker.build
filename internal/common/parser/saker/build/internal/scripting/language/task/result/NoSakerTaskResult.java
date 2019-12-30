package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;

public final class NoSakerTaskResult implements SakerTaskResult {
	private static final long serialVersionUID = 1L;

	private TaskIdentifier taskid;

	/**
	 * For {@link Externalizable}.
	 */
	public NoSakerTaskResult() {
	}

	public NoSakerTaskResult(TaskIdentifier taskid) {
		this.taskid = taskid;
	}

	@Override
	public Object get(TaskResultResolver results) {
		throw new OperandExecutionException("Task produced no results.", taskid);
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		throw new OperandExecutionException("Task produced no results.", taskid);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskid);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskid = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskid == null) ? 0 : taskid.hashCode());
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
		NoSakerTaskResult other = (NoSakerTaskResult) obj;
		if (taskid == null) {
			if (other.taskid != null)
				return false;
		} else if (!taskid.equals(other.taskid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + taskid + "]";
	}

}
