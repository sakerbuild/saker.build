package testing.saker.build.tests.tasks;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;

@SakerTest
public class LaterDependencyChangerTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");
		files.putFile(filepath, "content");

		SequentialChildTaskStarterTaskFactory task = new SequentialChildTaskStarterTaskFactory();
		task.add(strTaskId("reader"), new FileStringContentTaskFactory(filepath));
		task.add(strTaskId("creator"), new StringFileOutputTaskFactory(filepath, "text"));

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "content").contains(strTaskId("creator"), null).noRemaining();
		assertEquals(files.getAllBytes(filepath).toString(), "text");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "text").noRemaining();

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		files.putFile(filepath, "modified");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "modified").contains(strTaskId("creator"), null).noRemaining();
		assertEquals(files.getAllBytes(filepath).toString(), "text");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "text").noRemaining();

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();
	}

}
