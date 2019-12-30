package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MultiIncludeWorkingDirectoryTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("nowd"), PATH_WORKING_DIRECTORY);
		assertEquals(res.getTargetTaskResult("subwd"), PATH_WORKING_DIRECTORY.resolve("sub"));
		assertEquals(res.getTargetTaskResult("nowdres"), PATH_WORKING_DIRECTORY.resolve("resolve"));
		assertEquals(res.getTargetTaskResult("subwdres"), PATH_WORKING_DIRECTORY.resolve("sub").resolve("resolve"));
	}

}
