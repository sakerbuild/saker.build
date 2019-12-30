package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class PathBuiltinTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("wd"), PATH_WORKING_DIRECTORY);
		assertEquals(res.getTargetTaskResult("rel"), PATH_WORKING_DIRECTORY.resolve("abc"));
		assertEquals(res.getTargetTaskResult("abs"), SakerPath.valueOf("/home/user"));
		assertEquals(res.getTargetTaskResult("sec"), PATH_WORKING_DIRECTORY.resolve("dir/sec"));
		assertEquals(res.getTargetTaskResult("secabs"), SakerPath.valueOf("/sec/abs"));

		assertEquals(res.getTargetTaskResult("varpath"), PATH_WORKING_DIRECTORY.resolve("varpath"));
	}

}
