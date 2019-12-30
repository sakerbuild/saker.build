package testing.saker.build.tests.data;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.task.TaskContext;
import saker.build.util.java.JavaTools;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class JdkClassSerializationTest extends CollectingMetricEnvironmentTestCase {

	private static class TestTaskResult implements Externalizable {
		private static final long serialVersionUID = 1L;

		private Class<?> clazz;

		public TestTaskResult() {
		}

		public TestTaskResult(Class<?> clazz) {
			this.clazz = clazz;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(clazz);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			clazz = (Class<?>) in.readObject();
		}
	}

	private static class SerializingTaskFactory extends SelfStatelessTaskFactory<TestTaskResult> {
		private static final long serialVersionUID = 1L;

		@Override
		public TestTaskResult run(TaskContext taskcontext) throws Exception {
			return new TestTaskResult(
					Class.forName("com.sun.source.tree.Tree", false, JavaTools.getJDKToolsClassLoader()));
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new SerializingTaskFactory());

		runTask("main", new SerializingTaskFactory());
		assertEmpty(getMetric().getRunTaskIdResults());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//no project, so the task results are serialized and deserialized
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(false).build();
	}

}
