package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class SimpleClusterTaskTest extends ClusterBuildTestCase {
	public static class RemoteableStringTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		public RemoteableStringTaskFactory() {
		}

		public RemoteableStringTaskFactory(String value) {
			this.value = value;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertEquals(
					taskcontext.getExecutionContext().getEnvironment().getUserParameters()
							.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM),
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
			return value;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			RemoteableStringTaskFactory other = (RemoteableStringTaskFactory) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "RemoteableStringTaskFactory [" + (value != null ? "value=" + value : "") + "]";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		RemoteableStringTaskFactory task = new RemoteableStringTaskFactory("value");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "value").noRemaining();

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		task = new RemoteableStringTaskFactory("modified");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "modified").noRemaining();
	}

}
