package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class DefaultsFileWorkingDirectoryTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		setDefaultsFileScriptOption(parameters, "defaultsdir/defaults.build");
		CombinedTargetTaskResult res = runScriptTask("build", PATH_WORKING_DIRECTORY.resolve("scriptdir/saker.build"));
		assertEquals(res.getTargetTaskResult("value"), "wd:/defaultsdir/abc");
	}

}
