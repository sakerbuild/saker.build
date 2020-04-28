package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class ListTaskNamesDefaultsFileTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("echo"), "123");
		assertEquals(res.getTargetTaskResult("concat"), "abcdef");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
