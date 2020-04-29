package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class DefaultDefaultsFileParamsTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "abc");
		assertEquals(res.getTargetTaskResult("overridden"), "over");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH,
				files.getAllBytes(DEFAULT_DEFAULTS_FILE_PATH).toString().replace("abc", "mod"));
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "mod");
		assertEquals(res.getTargetTaskResult("overridden"), "over");
		
		files.putFile(DEFAULT_DEFAULTS_FILE_PATH,
				files.getAllBytes(DEFAULT_DEFAULTS_FILE_PATH).toString().replace("Input: mod", ""));
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), null);
		assertEquals(res.getTargetTaskResult("overridden"), "over");
	}

}
