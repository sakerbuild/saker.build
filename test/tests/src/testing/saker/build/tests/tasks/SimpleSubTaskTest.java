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
public class SimpleSubTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory hellotask = new StringTaskFactory("hello");
		StringTaskFactory worldtask = new StringTaskFactory("world");
		StringTaskFactory world2task = new StringTaskFactory("world2");
		StringTaskFactory addedtask = new StringTaskFactory("added");

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), hellotask).put(strTaskId("c2"), worldtask).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "c1", "c2"));

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), hellotask).put(strTaskId("c2"), worldtask).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), hellotask).put(strTaskId("c2"), world2task).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "c2"));

		runTask("main",
				new ChildTaskStarterTaskFactory(TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("c1"), hellotask).put(strTaskId("c2"), world2task)
						.put(strTaskId("c3"), addedtask).build()));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "c3"));
	}
}
