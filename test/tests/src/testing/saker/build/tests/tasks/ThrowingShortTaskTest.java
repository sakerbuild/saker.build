package testing.saker.build.tests.tasks;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FailerTaskFactory;

@SakerTest
public class ThrowingShortTaskTest extends CollectingMetricEnvironmentTestCase {
	//checks that if a short task is started and throws an exception it is not propagated to the starter without retrieving

	private static class ThrowingStarterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		public ThrowingStarterTaskFactory() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.startTask(strTaskId("thrower"), new FailerTaskFactory(MyException.class, "fail"), null);
			return "result";
		}
	}

	public static class MyException extends Exception {
		private static final long serialVersionUID = 1L;

		public MyException(String message) {
			super(message);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(MyException.class, () -> runTask("main", new ThrowingStarterTaskFactory()));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "result");
	}

}
