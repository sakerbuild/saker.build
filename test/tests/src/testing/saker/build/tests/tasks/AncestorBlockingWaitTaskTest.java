package testing.saker.build.tests.tasks;

import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.ExecutionOrderer;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class AncestorBlockingWaitTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final String SECTION_WAITER_START = "waiter_start";
	private static final String SECTION_WAITER_END = "waiter_end";
	private static final String SECTION_BLOCKER_START = "blocker_start";
	private static final String SECTION_BLOCKER_END = "blocker_end";
	private static final String SECTION_BLOCKER_STARTED = "blocker_started";

	private static ExecutionOrderer orderer;
	private static volatile boolean gotStrTaskResultByWaiter = false;

	private static class StarterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("waiter"), new WaiterTaskFactory());
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("blocker"), new BlockerStarterTaskFactory());
			return null;
		}

	}

	private static class WaiterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter(SECTION_WAITER_START);
			taskcontext.getTaskResult(strTaskId("str"));
			gotStrTaskResultByWaiter = true;
			orderer.enter(SECTION_WAITER_END);
			return null;
		}

	}

	private static class BlockerStarterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter(SECTION_BLOCKER_START);
			TaskFuture<String> strfut = taskcontext.getTaskUtilities().startTaskFuture(strTaskId("str"),
					new StringTaskFactory("str"));
			orderer.enter(SECTION_BLOCKER_STARTED);
			strfut.get();
			Thread.sleep(100);
			orderer.enter(SECTION_BLOCKER_END);
			assertEquals(orderer.getNextSection(), SECTION_WAITER_END);
			assertFalse(gotStrTaskResultByWaiter);
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		gotStrTaskResultByWaiter = false;

		ExecutionOrderer orderer = new ExecutionOrderer();
		orderer.addSection(SECTION_WAITER_START);
		orderer.addSection(SECTION_BLOCKER_START);
		orderer.addSection(SECTION_BLOCKER_STARTED);
		orderer.addSection(SECTION_BLOCKER_END);
		orderer.addSection(SECTION_WAITER_END);
		TaskFactory<?> main = new StarterTaskFactory();

		AncestorBlockingWaitTaskTest.orderer = new ExecutionOrderer(orderer);
		runTask("main", main);
	}

}
