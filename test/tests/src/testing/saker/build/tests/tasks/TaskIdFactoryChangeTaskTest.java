package testing.saker.build.tests.tasks;

import java.util.HashMap;

import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskIdFactoryChangeTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory hellotask = new StringTaskFactory("hello");
		StringTaskFactory worldtask = new StringTaskFactory("world");

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), hellotask).put(strTaskId("c2"), hellotask).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "c1", "c2"));

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), hellotask).put(strTaskId("c2"), worldtask).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "c2"));

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), worldtask).put(strTaskId("c2"), worldtask).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "c1"));
	}

}
