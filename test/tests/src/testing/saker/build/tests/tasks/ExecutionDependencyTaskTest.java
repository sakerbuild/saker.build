package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.util.property.IDEConfigurationRequiredExecutionProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class ExecutionDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class PropertyDependentTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String baseResult;

		public PropertyDependentTaskFactory() {
		}

		public PropertyDependentTaskFactory(String baseResult) {
			this.baseResult = baseResult;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(baseResult);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			baseResult = (String) in.readObject();
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return baseResult + ":" + taskcontext.getTaskUtilities()
					.getReportExecutionDependency(IDEConfigurationRequiredExecutionProperty.INSTANCE);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((baseResult == null) ? 0 : baseResult.hashCode());
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
			PropertyDependentTaskFactory other = (PropertyDependentTaskFactory) obj;
			if (baseResult == null) {
				if (other.baseResult != null)
					return false;
			} else if (!baseResult.equals(other.baseResult))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PropertyDependentTaskFactory [" + (baseResult != null ? "baseResult=" + baseResult : "") + "]";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory().add(strTaskId("ider"),
				new PropertyDependentTaskFactory("base"));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider"), "base:false");

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		parameters.setRequiresIDEConfiguration(true);

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider"), "base:true").noRemaining();

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		parameters.setRequiresIDEConfiguration(false);
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider"), "base:false").noRemaining();
	}

}
