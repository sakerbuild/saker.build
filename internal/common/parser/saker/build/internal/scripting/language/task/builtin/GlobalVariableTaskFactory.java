package saker.build.internal.scripting.language.task.builtin;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Objects;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.GlobalVariableTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.GlobalValueTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class GlobalVariableTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory nameFactory;

	/**
	 * For {@link Externalizable}.
	 */
	public GlobalVariableTaskFactory() {
	}

	public GlobalVariableTaskFactory(SakerTaskFactory nameFactory) {
		this.nameFactory = nameFactory;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier namefactoryid = nameFactory
				.createSubTaskIdentifier((SakerScriptTaskIdentifier) taskcontext.getTaskId());
		String varnamestr = Objects.toString(
				taskcontext.getTaskUtilities().runTaskResult(namefactoryid, nameFactory).toResult(taskcontext), null);
		if (varnamestr == null) {
			taskcontext.abortExecution(
					new OperandExecutionException("Global variable name evaluated to null.", namefactoryid));
			return null;
		}
		GlobalVariableTaskResult result = new GlobalVariableTaskResult(new GlobalValueTaskIdentifier(varnamestr));
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new GlobalVariableTaskFactory(cloneHelper(taskfactoryreplacements, nameFactory));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(nameFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		nameFactory = (SakerTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((nameFactory == null) ? 0 : nameFactory.hashCode());
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
		GlobalVariableTaskFactory other = (GlobalVariableTaskFactory) obj;
		if (nameFactory == null) {
			if (other.nameFactory != null)
				return false;
		} else if (!nameFactory.equals(other.nameFactory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (nameFactory != null ? "nameFactory=" + nameFactory : "") + "]";
	}

}
