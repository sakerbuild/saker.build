package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

/**
 * Checks that the build targets are invoked again if the script option for the defaults file changes.
 */
@SakerTest
public class DefaultsFileOptionChangeTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "abc");

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "abc");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());

		setDefaultsFileScriptOption(parameters, "defaultsxyz.build");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "xyz");

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "xyz");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());

		setDefaultsFileScriptOption(parameters, "defaults123.build");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "123");

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "123");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());
	}

}
