package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.util.property.BuildTimeExecutionProperty;

public class BuildTimeStringTaskFactory implements TaskFactory<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public Task<String> createTask(ExecutionContext context) {
		return new Task<String>() {
			@Override
			public String run(TaskContext context) {
				long buildtimemillis = context.getTaskUtilities()
						.getReportExecutionDependency(BuildTimeExecutionProperty.INSTANCE);
				return new Date(buildtimemillis).toString();
			}
		};
	}

	@Override
	public int hashCode() {
		return this.getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "BuildTimeStringTaskFactory []";
	}

}
