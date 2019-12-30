package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import saker.build.scripting.TargetConfigurationReadingResult;
import saker.build.task.BuildTargetTaskFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ScriptLocationScriptTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);
		files.putFile(buildfilepath, "build(out i, out j,) {\n $i = 1 \n $j = 2\n }");

		TargetConfigurationReadingResult parsed = parseTestTargetConfigurationReadingResult(buildfilepath);
		BuildTargetTaskFactory buildtaskfactory = parsed.getTargetConfiguration().getTask("build");
		runTask("main", buildtaskfactory);
		assertEquals(parsed.getInformationProvider().getTargetPosition("build").getFileOffset(), 0);

		files.putFile(buildfilepath, "   build(out i, out j,) {\n $i = 1 \n $j = 2\n }");
		parsed = parseTestTargetConfigurationReadingResult(buildfilepath);
		buildtaskfactory = parsed.getTargetConfiguration().getTask("build");
		runTask("main", buildtaskfactory);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(parsed.getInformationProvider().getTargetPosition("build").getFileOffset(), 3);
	}

}
