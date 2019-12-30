package testing.saker.build.tests.tasks;

import java.io.IOException;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskAbandonTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		simpleAbandon();
		multiRootAbandon();
		cascadeAbandon();
		abandonedBuildDirectoryFileDeleteTest();
		abandonedWorkingDirectoryFileDeleteTest();
	}

	private void simpleAbandon() throws Throwable {
		ChildTaskStarterTaskFactory bothtask = new ChildTaskStarterTaskFactory();
		bothtask.add(strTaskId("sub1"), new StringTaskFactory("v1"));
		bothtask.add(strTaskId("sub2"), new StringTaskFactory("v2"));

		ChildTaskStarterTaskFactory removedtask = new ChildTaskStarterTaskFactory();
		removedtask.add(strTaskId("sub1"), new StringTaskFactory("v1"));

		runTask("main", bothtask);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main", "sub1", "sub2"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());

		runTask("main", removedtask);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("sub2"));

		runTask("main", removedtask);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());
	}

	private void multiRootAbandon() throws Throwable {
		ChildTaskStarterTaskFactory root1 = new ChildTaskStarterTaskFactory();
		root1.add(strTaskId("rsub1"), new StringTaskFactory("v1"));
		root1.add(strTaskId("rsub2"), new StringTaskFactory("v2"));

		ChildTaskStarterTaskFactory root2 = new ChildTaskStarterTaskFactory();
		root2.add(strTaskId("rsub2"), new StringTaskFactory("v2"));
		root2.add(strTaskId("rsub3"), new StringTaskFactory("v3"));
		root2.add(strTaskId("rsub4"), new StringTaskFactory("v4"));

		ChildTaskStarterTaskFactory removertask = new ChildTaskStarterTaskFactory();
		removertask.add(strTaskId("rx"), new StringTaskFactory("vx"));

		runTask("r1", root1);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("r1", "rsub1", "rsub2"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());

		runTask("r2", root2);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("r2", "rsub3", "rsub4"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());

		runTask("r1", removertask);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("r1", "rx"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("rsub1"));

		runTask("r2", removertask);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("r2"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("rsub2", "rsub3", "rsub4"));
	}

	private void cascadeAbandon() throws Throwable {
		ChildTaskStarterTaskFactory root1 = new ChildTaskStarterTaskFactory();
		root1.add(strTaskId("1"),
				new ChildTaskStarterTaskFactory().add(strTaskId("1/2"), new StringTaskFactory("1/2val")));

		runTask("t1", root1);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("t1", "1", "1/2"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());

		runTask("t1", root1);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());

		ChildTaskStarterTaskFactory root2 = new ChildTaskStarterTaskFactory();
		root2.add(strTaskId("2"),
				new ChildTaskStarterTaskFactory().add(strTaskId("2/2"), new StringTaskFactory("2/2val")));
		runTask("t1", root2);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("t1", "2", "2/2"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("1", "1/2"));

		runTask("t1", root2);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf());
	}

	private void abandonedBuildDirectoryFileDeleteTest() throws Throwable {
		ChildTaskStarterTaskFactory root1 = new ChildTaskStarterTaskFactory();
		SakerPath fileoutoutpath = PATH_BUILD_DIRECTORY.resolve("out.txt");
		root1.add(strTaskId("1"), new StringFileOutputTaskFactory(fileoutoutpath, "output"));

		runTask("t1", root1);
		assertEquals(files.getAllBytes(fileoutoutpath).toString(), "output");

		ChildTaskStarterTaskFactory root2 = new ChildTaskStarterTaskFactory();
		root2.add(strTaskId("2"), new StringTaskFactory("stroutput"));
		runTask("t1", root2);
		if (project != null) {
			//we wait for the execution finalization to be finished
			project.waitExecutionFinalization();
		}
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("1"));
		//assert that it has been deleted
		assertException(IOException.class, () -> files.getAllBytes(fileoutoutpath));
	}

	private void abandonedWorkingDirectoryFileDeleteTest() throws Throwable {
		ChildTaskStarterTaskFactory root1 = new ChildTaskStarterTaskFactory();
		SakerPath fileoutoutpath = PATH_WORKING_DIRECTORY.resolve("out.txt");
		root1.add(strTaskId("1"), new StringFileOutputTaskFactory(fileoutoutpath, "output"));

		runTask("t1", root1);
		assertEquals(files.getAllBytes(fileoutoutpath).toString(), "output");

		ChildTaskStarterTaskFactory root2 = new ChildTaskStarterTaskFactory();
		root2.add(strTaskId("2"), new StringTaskFactory("stroutput"));
		runTask("t1", root2);
		if (project != null) {
			//we wait for the execution finalization to be finished
			project.waitExecutionFinalization();
		}
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("1"));
		//assert that it has not been deleted
		assertEquals(files.getAllBytes(fileoutoutpath).toString(), "output");
	}
}
