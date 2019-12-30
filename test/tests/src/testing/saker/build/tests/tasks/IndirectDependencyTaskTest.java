package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class IndirectDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class TestTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) throws Exception {
					ChildTaskStarterTaskFactory starter = new ChildTaskStarterTaskFactory().add(strTaskId("dependent"),
							new StringTaskFactory("value"));
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId("child"), starter);
					return taskcontext.getTaskResult(strTaskId("dependent")).toString();
				}
			};
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TestTaskFactory task = new TestTaskFactory();

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "value");

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
	}

}
