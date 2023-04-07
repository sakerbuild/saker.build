package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

public class StringEnvironmentPropertyDependentTaskFactory implements TaskFactory<String>, Externalizable, Task<String> {
	private static final long serialVersionUID = 1L;

	protected EnvironmentProperty<String> dependency;

	public StringEnvironmentPropertyDependentTaskFactory() {
	}

	public StringEnvironmentPropertyDependentTaskFactory(EnvironmentProperty<String> dependency) {
		this.dependency = dependency;
	}

	@Override
	public Task<? extends String> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public String run(TaskContext taskcontext) throws Exception {
		return taskcontext.getTaskUtilities().getReportEnvironmentDependency(dependency);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(dependency);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		dependency = (EnvironmentProperty<String>) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dependency == null) ? 0 : dependency.hashCode());
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
		StringEnvironmentPropertyDependentTaskFactory other = (StringEnvironmentPropertyDependentTaskFactory) obj;
		if (dependency == null) {
			if (other.dependency != null)
				return false;
		} else if (!dependency.equals(other.dependency))
			return false;
		return true;
	}
}