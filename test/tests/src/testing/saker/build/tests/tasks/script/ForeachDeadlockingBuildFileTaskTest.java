package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

/**
 * This test is for a specific bug where modifying a variable in an included build file caused the caller to be
 * deadlocked if the result was used in a for-each control structure.
 */
@SakerTest
public class ForeachDeadlockingBuildFileTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath testbuildpath = PATH_WORKING_DIRECTORY.resolve("test.build");
		SakerPath otherbuildpath = PATH_WORKING_DIRECTORY.resolve("other.build");
		runScriptTask("test", testbuildpath);

		//with this modification, the execution should not deadlock
		files.putFile(otherbuildpath,
				files.getAllBytes(otherbuildpath).toString().replace("theelemvalue", "\"{$Input}\""));
		runScriptTask("test", testbuildpath);

	}

}
