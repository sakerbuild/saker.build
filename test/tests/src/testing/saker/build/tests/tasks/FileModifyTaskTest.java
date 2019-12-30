package testing.saker.build.tests.tasks;

import java.util.HashMap;

import saker.build.file.path.SakerPath;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class FileModifyTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");

		FileStringContentTaskFactory filetask = new FileStringContentTaskFactory(filepath);
		files.putFile(filepath, "hello");

		runTask("file", filetask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));
		runTask("file", filetask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(filepath, "world");
		runTask("file", filetask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));

		ChildTaskStarterTaskFactory childer = new ChildTaskStarterTaskFactory(TestUtils
				.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>()).put(strTaskId("file"), filetask).build());

		runTask("childer", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("childer"));

		files.putFile(filepath, "subworld");
		runTask("childer", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));
	}

}
