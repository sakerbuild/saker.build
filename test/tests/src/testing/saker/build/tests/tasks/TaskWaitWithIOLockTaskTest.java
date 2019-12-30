package testing.saker.build.tests.tasks;

import java.util.Objects;

import saker.build.task.TaskContext;
import saker.build.task.exception.IllegalTaskOperationException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskWaitWithIOLockTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class WaiterTask extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.acquireStandardIOLock();
			try {
				return Objects.toString(taskcontext.getTaskResult(strTaskId("str")));
			} finally {
				taskcontext.releaseStandardIOLock();
			}
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory();
		main.add(strTaskId("str"), new StringTaskFactory("content"));
		main.add(strTaskId("waiter"), new WaiterTask());

		assertTaskException(IllegalTaskOperationException.class, () -> runTask("main", main));
	}

}
