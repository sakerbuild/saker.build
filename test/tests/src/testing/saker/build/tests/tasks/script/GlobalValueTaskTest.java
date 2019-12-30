package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class GlobalValueTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "123");

		res = runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());
		assertEquals(res.getTargetTaskResult("value"), "123");
	}

}
