package testing.saker.build.tests.tasks;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class MissingInputDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("input.txt");

		FileStringContentTaskFactory task = new FileStringContentTaskFactory(filepath);

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), setOf());

		files.putFile(filepath, "content");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "content").noRemaining();

		files.putFile(filepath, "modifiedcontent");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "modifiedcontent").noRemaining();

		files.deleteRecursively(filepath);
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();
	}

}
