package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.delta.impl.OutputLoadFailedDeltaImpl;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class OutputLoadErrorTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class ErrorLoader implements Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			throw new IOException("Failed to load.");
		}

	}

	public static class LoaderTaskFactory implements TaskFactory<ErrorLoader>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public Task<ErrorLoader> createTask(ExecutionContext executioncontext) {
			return new Task<OutputLoadErrorTaskTest.ErrorLoader>() {
				@Override
				public ErrorLoader run(TaskContext taskcontext) {
					return new ErrorLoader();
				}
			};
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		LoaderTaskFactory task = new LoaderTaskFactory();

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		runTask("main", task);
		if (project == null) {
			assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
			assertMap(getMetric().getRunTaskIdDeltas()).contains(strTaskId("main"),
					setOf(OutputLoadFailedDeltaImpl.INSTANCE));
		} else {
			assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
			assertMap(getMetric().getRunTaskIdDeltas()).noRemaining();
		}
	}

}
