package testing.saker.build.tests.tasks.script;

import java.util.Arrays;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class EqualityOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), 1 == 3);
		assertEquals(result.getTargetTaskResult("b"), 1 != 3);
		assertEquals(result.getTargetTaskResult("c"), 1 == 1);
		assertEquals(result.getTargetTaskResult("d"), 1 != -2);
		assertEquals(result.getTargetTaskResult("e"), 1 == 10);
		assertEquals(result.getTargetTaskResult("f"), 10 == 10);
		assertEquals(result.getTargetTaskResult("g"), 10 == 10);
		assertEquals(result.getTargetTaskResult("h"), 10 != 10);

		assertEquals(result.getTargetTaskResult("i"), 1 == 1 == true);
		assertEquals(result.getTargetTaskResult("j"), 1 == 2 == false);

		assertEquals(result.getTargetTaskResult("l"), true);
		assertEquals(result.getTargetTaskResult("m"), true);
		assertEquals(result.getTargetTaskResult("num"), true);

		assertEquals(result.getTargetTaskResult("alltrues"), Arrays.asList(true, true, true, true, true, true));
	}
}
