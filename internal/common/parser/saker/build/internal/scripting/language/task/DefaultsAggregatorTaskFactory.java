package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.Set;

import saker.build.internal.scripting.language.task.result.SakerScriptTaskDefaults;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class DefaultsAggregatorTaskFactory
		implements TaskFactory<SakerScriptTaskDefaults>, Task<SakerScriptTaskDefaults>, TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<DefaultsLoaderTaskFactory> tasks;

	/**
	 * For {@link Externalizable}.
	 */
	public DefaultsAggregatorTaskFactory() {
	}

	public DefaultsAggregatorTaskFactory(Set<DefaultsLoaderTaskFactory> tasks) {
		this.tasks = tasks;
	}

	@Override
	public SakerScriptTaskDefaults run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskDefaults result = loadDefaults(taskcontext);

		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	private SakerScriptTaskDefaults loadDefaults(TaskContext taskcontext) {
		Iterator<DefaultsLoaderTaskFactory> it = tasks.iterator();
		if (!it.hasNext()) {
			return SakerScriptTaskDefaults.EMPTY_INSTANCE;
		}

		DefaultsLoaderTaskFactory t = it.next();
		if (!it.hasNext()) {
			return (SakerScriptTaskDefaults) taskcontext.getTaskResult(t);
		}

		SakerScriptTaskDefaults result = new SakerScriptTaskDefaults();
		do {
			SakerScriptTaskDefaults defs = (SakerScriptTaskDefaults) taskcontext.getTaskResult(t);
			result.add(defs);
			t = it.next();
		} while (it.hasNext());
		return result;
	}

	@Override
	public Task<? extends SakerScriptTaskDefaults> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, tasks);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		tasks = SerialUtils.readExternalImmutableHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((tasks == null) ? 0 : tasks.hashCode());
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
		DefaultsAggregatorTaskFactory other = (DefaultsAggregatorTaskFactory) obj;
		if (tasks == null) {
			if (other.tasks != null)
				return false;
		} else if (!tasks.equals(other.tasks))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DefaultsAggregatorTaskFactory[tasks=" + tasks + "]";
	}

}
