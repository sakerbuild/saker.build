package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class DefaultsFileIncrementalityTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), null);

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH, "");
		res = runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		assertEquals(res.getTargetTaskResult("value"), null);

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH, "defaults(example.echo, Input: abc)");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "abc");

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH, "defaults(example.echo, Input: xyz)");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "xyz");

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH, "");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), null);

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH, "defaults(example.echo, Input: 123)");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "123");

		files.delete(DEFAULT_DEFAULTS_FILE_PATH);
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), null);
		assertEquals(System.clearProperty(EXAMPLE_ECHO_RUN_PROPERTY_NAME), "");

		files.putFile(DEFAULT_DEFAULTS_FILE_PATH, "defaults(some.other.task, Input: 123)");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), null);
		//make sure the task which is not affected by default modifications are not rerun
		assertEquals(System.clearProperty(EXAMPLE_ECHO_RUN_PROPERTY_NAME), null);
	}

}
