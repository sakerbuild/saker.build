package testing.saker.build.tests.tasks;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class DependencyDatabaseDeleteCleanTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory main = new StringTaskFactory("str");
		runTask("main", main);

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		if (project != null) {
			project.waitExecutionFinalization();
		}
		SakerPath databasepath = PATH_BUILD_DIRECTORY.resolve("dependencies.map");
		assertTrue(files.getFileAttributes(databasepath).isRegularFile());
		files.delete(databasepath);

		runTask("main", main);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
	}

}
