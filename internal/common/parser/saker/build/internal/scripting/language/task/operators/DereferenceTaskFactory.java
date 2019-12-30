package saker.build.internal.scripting.language.task.operators;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.NavigableSet;

import saker.build.internal.scripting.language.exc.InvalidScriptDeclarationTaskFactory;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerScriptTaskUtils;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.DereferenceSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.scripting.ScriptPosition;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class DereferenceTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected SakerTaskFactory subject;

	public DereferenceTaskFactory() {
	}

	private DereferenceTaskFactory(SakerTaskFactory subject) {
		this.subject = subject;
	}

	public static SakerTaskFactory create(SakerTaskFactory subject) {
		if (subject instanceof SakerLiteralTaskFactory) {
			Object val = ((SakerLiteralTaskFactory) subject).getValue();
			if (val == null) {
				return new DereferenceTaskFactory(subject);
			}
			return new DereferenceLiteralTaskFactory(val.toString());
		}
		return new DereferenceTaskFactory(subject);
	}

	public static SakerTaskFactory create(SakerTaskFactory subject, ScriptPosition scriptposition) {
		if (subject instanceof SakerLiteralTaskFactory) {
			Object val = ((SakerLiteralTaskFactory) subject).getValue();
			if (val == null) {
				return new InvalidScriptDeclarationTaskFactory("Failed to dereference null variable.", scriptposition);
			}
			return new DereferenceLiteralTaskFactory(val.toString());
		}
		return new DereferenceTaskFactory(subject);
	}

	@Override
	public NavigableSet<String> getCapabilities() {
		return SakerScriptTaskUtils.CAPABILITIES_SHORT_TASK;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier nametaskid = subject.createSubTaskIdentifier(thistaskid);
		taskcontext.getTaskUtilities().startTaskFuture(nametaskid, subject);

		DereferenceSakerTaskResult result = new DereferenceSakerTaskResult(thistaskid, nametaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new DereferenceTaskFactory(cloneHelper(taskfactoryreplacements, subject));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((subject == null) ? 0 : subject.hashCode());
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
		DereferenceTaskFactory other = (DereferenceTaskFactory) obj;
		if (subject == null) {
			if (other.subject != null)
				return false;
		} else if (!subject.equals(other.subject))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(deref:($" + subject + "))";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(subject);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		subject = (SakerTaskFactory) in.readObject();
	}

}
