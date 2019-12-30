package saker.build.internal.scripting.language.task;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.NavigableSet;

import saker.build.internal.scripting.language.task.result.SakerTaskObjectSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class SakerTaskResultLiteralTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier sakerTaskId;

	public SakerTaskResultLiteralTaskFactory() {
	}

	public SakerTaskResultLiteralTaskFactory(TaskIdentifier taskId) {
		this.sakerTaskId = taskId;
	}

	@Override
	public NavigableSet<String> getCapabilities() {
		return SakerScriptTaskUtils.CAPABILITIES_SHORT_TASK;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerTaskResult result = new SakerTaskObjectSakerTaskResult(sakerTaskId);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
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
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(sakerTaskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		sakerTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((sakerTaskId == null) ? 0 : sakerTaskId.hashCode());
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
		SakerTaskResultLiteralTaskFactory other = (SakerTaskResultLiteralTaskFactory) obj;
		if (sakerTaskId == null) {
			if (other.sakerTaskId != null)
				return false;
		} else if (!sakerTaskId.equals(other.sakerTaskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(saker-result:" + sakerTaskId + ")";
	}
}
