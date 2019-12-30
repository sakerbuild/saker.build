package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class SimpleInnerTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class InnerTaskStarter implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		private NavigableSet<String> capabilities;

		/**
		 * For {@link Externalizable}.
		 */
		public InnerTaskStarter() {
		}

		public InnerTaskStarter(String value) {
			this.value = value;
		}

		public InnerTaskStarter setCapabilities(NavigableSet<String> capabilities) {
			this.capabilities = capabilities;
			return this;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			StringTaskFactory innertf = new StringTaskFactory(value);
			innertf.setCapabilities(capabilities);
			return taskcontext.startInnerTask(innertf, null).getNext().getResult();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
			out.writeObject(capabilities);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
			capabilities = (NavigableSet<String>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((capabilities == null) ? 0 : capabilities.hashCode());
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
			InnerTaskStarter other = (InnerTaskStarter) obj;
			if (capabilities == null) {
				if (other.capabilities != null)
					return false;
			} else if (!capabilities.equals(other.capabilities))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new InnerTaskStarter("hello")
				.setCapabilities(ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_SHORT_TASK)));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "hello");

		runTask("main2", new InnerTaskStarter("hello2"));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main2")), "hello2");
	}

}
