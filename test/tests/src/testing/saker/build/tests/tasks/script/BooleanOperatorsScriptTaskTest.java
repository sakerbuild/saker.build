package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class BooleanOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), true || false);
		assertEquals(result.getTargetTaskResult("b"), true && false);
		assertEquals(result.getTargetTaskResult("c"), true && true);
		assertEquals(result.getTargetTaskResult("d"), false && false);
		assertEquals(result.getTargetTaskResult("e"), false || false);
		assertEquals(result.getTargetTaskResult("f"), false || true);
		assertEquals(result.getTargetTaskResult("g"), true && true);
		assertEquals(result.getTargetTaskResult("h"), false && true || false);
		assertEquals(result.getTargetTaskResult("i"), true && false || false);
		assertEquals(result.getTargetTaskResult("j"), false && false || true);
	}
}
