package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ForeachLocalScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), listOf(3L, 33L));

		res = runScriptTask("nested");
		assertEquals(res.getTargetTaskResult("result"), listOf(listOf(1L, 2L), listOf(11L, 22L)));

		res = runScriptTask("lateinit");
		assertEquals(res.getTargetTaskResult("result"), listOf(4L, 44L));

		res = runScriptTask("mixed");
		assertEquals(res.getTargetTaskResult("result"), listOf(5L, 55L));

		res = runScriptTask("calclocal");
		assertEquals(res.getTargetTaskResult("result"), 1L);

		//to ensure that multiple foreach loops don't conflict if they have the same loop item and local name
		//and both assign them
		res = runScriptTask("multisamelocals");
	}

}
