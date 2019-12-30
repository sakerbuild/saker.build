package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MultiplyOperatorScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), 1L * 3L);
		assertEquals(result.getTargetTaskResult("b"), 1L * -3L);
		assertEquals(result.getTargetTaskResult("c"), 1.2d * 3.4d);
		assertEquals(result.getTargetTaskResult("d"), -1.2d * -3.4d * 5.6d);
		assertEquals(result.getTargetTaskResult("e"), 1L * 10L);
		assertEquals(result.getTargetTaskResult("f"), 10L * 10L);
	}
}
