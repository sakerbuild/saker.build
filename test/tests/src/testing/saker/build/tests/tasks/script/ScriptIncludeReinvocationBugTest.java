package testing.saker.build.tests.tasks.script;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class ScriptIncludeReinvocationBugTest extends CollectingMetricEnvironmentTestCase {

	private static final AtomicInteger invoked = new AtomicInteger();

	private static class PseudoTask implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public PseudoTask() {
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Void>() {

				@SakerInput("Input")
				public String in;

				@Override
				public Void run(TaskContext taskcontext) throws Exception {
					invoked.getAndIncrement();
					return null;
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> injected = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		injected.put(TaskName.valueOf("test.pseudo.task"), new PseudoTask());
		result.setInjectedTaskFactories(injected);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		invoked.set(0);

		runScriptTask("build");
		assertEquals(invoked.getAndSet(0), 1);

		runScriptTask("build");
		assertEquals(invoked.getAndSet(0), 0);

		files.touch(PATH_WORKING_DIRECTORY.resolve("saker.build"));
		runScriptTask("build");
		assertEquals(invoked.getAndSet(0), 0);
	}

}
