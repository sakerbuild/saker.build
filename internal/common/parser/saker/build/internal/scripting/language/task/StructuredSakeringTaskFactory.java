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
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class StructuredSakeringTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected StructuredTaskResult object;

	/**
	 * For {@link Externalizable}.
	 */
	public StructuredSakeringTaskFactory() {
	}

	public StructuredSakeringTaskFactory(StructuredTaskResult object) {
		this.object = object;
	}

	@Override
	public NavigableSet<String> getCapabilities() {
		return SakerScriptTaskUtils.CAPABILITIES_SHORT_TASK;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SimpleSakerTaskResult<StructuredTaskResult> result = new SimpleSakerTaskResult<>(object);
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
		out.writeObject(object);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		object = (StructuredTaskResult) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
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
		StructuredSakeringTaskFactory other = (StructuredSakeringTaskFactory) obj;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[object=" + object + "]";
	}

}
