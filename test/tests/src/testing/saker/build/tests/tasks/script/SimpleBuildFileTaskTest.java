package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SimpleBuildFileTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");

		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());
	}

}
