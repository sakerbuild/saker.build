package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.task.BuildTargetTaskFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class BuildFileMoveReinvokeTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildfilepath1 = PATH_WORKING_DIRECTORY.resolve("saker.build");
		TargetConfiguration tc1 = parseTestTargetConfiguration(buildfilepath1);
		BuildTargetTaskFactory target1 = tc1.getTask("build");

		runTask("main", target1);
		assertNotEmpty(getMetric().getRunTaskIdResults());

		SakerPath buildfilepath2 = PATH_WORKING_DIRECTORY.resolve("moved.build");
		files.putFile(buildfilepath2, files.getAllBytes(buildfilepath1));
		files.deleteRecursively(buildfilepath1);

		TargetConfiguration tc2 = parseTestTargetConfiguration(buildfilepath2);
		BuildTargetTaskFactory target2 = tc2.getTask("build");
		runTask("main", target2);
		assertNotEmpty(getMetric().getRunTaskIdResults());
	}
}
