package testing.saker.build.tests.tasks;

import java.io.Externalizable;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.ExecutionOrderer;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class InnerTaskOverExecuteTaskTest extends CollectingMetricEnvironmentTestCase {
	private static ExecutionOrderer orderer;

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public InnerTaskStarter() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter("TASK_START");
			taskcontext.startInnerTask(new SleepingTaskFactory(), null);
			orderer.enter("TASK_END");
			return null;
		}
	}

	public static class SleepingTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter("INNER_START");
			Thread.sleep(200);
			taskcontext.startInnerTask(new StringTaskFactory("hello"), null).getNext();
			orderer.enter("INNER_END");
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ExecutionOrderer orderer = new ExecutionOrderer();
		orderer.addSection("TASK_START");
		orderer.addSection("TASK_END");
		orderer.addSection("INNER_START");
		orderer.addSection("INNER_END");

		InnerTaskOverExecuteTaskTest.orderer = new ExecutionOrderer(orderer);
		runTask("main", new InnerTaskStarter());
	}

}
