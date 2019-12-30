package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class StaticVariablesScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), 123L);
		assertEquals(result.getTargetTaskResult("condres"), 222L);

		result = runScriptTask("including");
		assertEquals(result.getTargetTaskResult("a"), 123L);
		assertEquals(result.getTargetTaskResult("set"), "set");
		assertEquals(result.getTargetTaskResult("condres"), 222L);

		result = runScriptTask("feedbacker");
		assertEquals(result.getTargetTaskResult("a"), 123L);
		assertEquals(result.getTargetTaskResult("condres"), 111L);
	}

}
