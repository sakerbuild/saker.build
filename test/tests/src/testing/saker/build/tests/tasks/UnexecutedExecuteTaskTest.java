package testing.saker.build.tests.tasks;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class UnexecutedExecuteTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory hello = new StringTaskFactory("hello");
		StringTaskFactory world = new StringTaskFactory("world");

		runTask("hello", hello);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("hello"));
		runTask("world", world);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("world"));

		runTask("hello", hello);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
		runTask("world", world);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
	}

}
