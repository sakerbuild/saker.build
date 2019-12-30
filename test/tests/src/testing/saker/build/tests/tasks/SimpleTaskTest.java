package testing.saker.build.tests.tasks;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class SimpleTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new StringTaskFactory("hello"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		runTask("main", new StringTaskFactory("hello"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		runTask("main", new StringTaskFactory("modified"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		runTask("main", new StringTaskFactory("modified"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
	}

}
