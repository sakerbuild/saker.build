package testing.saker.build.tests.tasks;

import java.util.HashMap;

import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.BuildTimeStringTaskFactory;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class BuildTimeDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		BuildTimeStringTaskFactory bttask = new BuildTimeStringTaskFactory();

		ChildTaskStarterTaskFactory childer = new ChildTaskStarterTaskFactory(TestUtils
				.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>()).put(strTaskId("time"), bttask).build());

		runTask("time", bttask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time"));

		Thread.sleep(20);
		runTask("time", bttask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time"));

		Thread.sleep(20);
		runTask("main", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time", "main"));

		Thread.sleep(20);
		runTask("main", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time"));
	}

}
