package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.internal.scripting.language.task.result.NoSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class DefaultsDeclarationSakerTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private NavigableSet<TaskName> defaultTaskNames;
	private NavigableMap<String, SakerTaskFactory> parameterDefaults;

	/**
	 * For {@link Externalizable}.
	 */
	public DefaultsDeclarationSakerTaskFactory() {
	}

	public DefaultsDeclarationSakerTaskFactory(NavigableSet<TaskName> deftasknames,
			NavigableMap<String, SakerTaskFactory> paramdefaults) {
		this.defaultTaskNames = deftasknames;
		this.parameterDefaults = paramdefaults;
	}

	public NavigableSet<TaskName> getDefaultTaskNames() {
		return defaultTaskNames;
	}

	public NavigableMap<String, SakerTaskFactory> getParameterDefaults() {
		return parameterDefaults;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		return new NoSakerTaskResult(taskcontext.getTaskId());
	}

	@Override
	protected boolean isShort() {
		// the task itself doesn't do anything, so it can be considered short
		return true;
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
		throw new UnsupportedOperationException(
				TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS + " cannot be started.");
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		SerialUtils.writeExternalCollection(out, defaultTaskNames);
		SerialUtils.writeExternalMap(out, parameterDefaults);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		defaultTaskNames = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		parameterDefaults = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((defaultTaskNames == null) ? 0 : defaultTaskNames.hashCode());
		result = prime * result + ((parameterDefaults == null) ? 0 : parameterDefaults.hashCode());
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
		DefaultsDeclarationSakerTaskFactory other = (DefaultsDeclarationSakerTaskFactory) obj;
		if (defaultTaskNames == null) {
			if (other.defaultTaskNames != null)
				return false;
		} else if (!defaultTaskNames.equals(other.defaultTaskNames))
			return false;
		if (parameterDefaults == null) {
			if (other.parameterDefaults != null)
				return false;
		} else if (!parameterDefaults.equals(other.parameterDefaults))
			return false;
		return true;
	}

}
