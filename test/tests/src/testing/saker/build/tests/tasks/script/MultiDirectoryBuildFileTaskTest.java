package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MultiDirectoryBuildFileTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("subout"), "suboutvaloutval");

		res = runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());
		assertEquals(res.getTargetTaskResult("subout"), "suboutvaloutval");
	}

}
