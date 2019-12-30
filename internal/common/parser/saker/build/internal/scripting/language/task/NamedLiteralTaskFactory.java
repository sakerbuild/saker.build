package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.NavigableSet;

import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;

public class NamedLiteralTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier taskId;
	protected transient Object value;

	/**
	 * For {@link Externalizable}.
	 */
	public NamedLiteralTaskFactory() {
	}

	public NamedLiteralTaskFactory(TaskIdentifier taskId, Object value) {
		this.taskId = taskId;
		this.value = value;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return this;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public TaskIdentifier createSubTaskIdentifier(SakerScriptTaskIdentifier parenttaskidentifier) {
		return taskId;
	}

	@Override
	public NavigableSet<String> getCapabilities() {
		return SakerScriptTaskUtils.CAPABILITIES_SHORT_TASK;
	}

	@Override
	public String toString() {
		return taskId.toString();
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		return new SimpleSakerTaskResult<>(value);
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
		NamedLiteralTaskFactory other = (NamedLiteralTaskFactory) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(taskId);
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		taskId = (TaskIdentifier) in.readObject();
		value = in.readObject();
	}
}