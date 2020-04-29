package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class TaskParamValueDefaultsFileTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("echo"), "123");
		assertEquals(res.getTargetTaskResult("over"), "987");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
