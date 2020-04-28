package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class DefaultDefaultsFileInvokedTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), 3L);

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH,
				files.getAllBytes(DEFAULT_DEFAULTS_FILE_PATH).toString().replace("3", "99"));
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), 99L);
	}

}
