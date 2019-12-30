package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class BuildFileStatementReorderTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);
		files.putFile(buildfilepath, "build(out i, out j,) {\n $i = 1 \n $j = 2\n }");
		runScriptTask("build");

		files.putFile(buildfilepath, "build(out i, out j,) {\n $j = 2 \n $i = 1 \n }");
		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdResults().size(), 1);

		files.putFile(buildfilepath, "build(out j, out i,) {\n $j = 2 \n $i = 1 \n }");
		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdResults().size(), 1);
	}

}
