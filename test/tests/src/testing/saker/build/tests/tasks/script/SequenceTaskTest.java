package testing.saker.build.tests.tasks.script;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.TreeMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.annot.SakerInput;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

@SakerTest
public class SequenceTaskTest extends VariablesMetricEnvironmentTestCase {
	private static class WaiterTask implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<String>() {
				@SakerInput(value = { "" }, required = true)
				public int Param;

				@Override
				public String run(TaskContext taskcontext) throws Exception {
					Thread.sleep(Param * 100);
					String currentval = System.getProperty("the.property", "");
					System.setProperty("the.property", currentval + Param);
					return Param + "";
				}
			};
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj != null && this.getClass() == obj.getClass();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	@Override
	protected CollectingTestMetric createMetricImpl() {
		CollectingTestMetric result = super.createMetricImpl();
		TreeMap<TaskName, TaskFactory<?>> injected = new TreeMap<>();
		injected.put(TaskName.valueOf("waiter.task"), new WaiterTask());
		result.setInjectedTaskFactories(injected);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		System.setProperty("the.property", "");

		runScriptTask("use");
		assertEquals(System.getProperty("the.property"), "321");

	}

}
