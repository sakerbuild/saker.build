package testing.saker.build.tests.tasks;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class DeltaDeadlockTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUT_FILE_PATH = PATH_WORKING_DIRECTORY.resolve("in.txt");

	private static class ChangingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerFile file = taskcontext.getTaskUtilities().resolveAtPath(INPUT_FILE_PATH);
			taskcontext.getTaskUtilities().reportInputFileDependency(null, file);
			if (!"wait".equals(file.getContent())) {

			} else {
				taskcontext.getTaskResult(strTaskId("spawned"));
			}
			return null;
		}
	}

	private static class SpawnerTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerFile file = taskcontext.getTaskUtilities().resolveAtPath(INPUT_FILE_PATH);
			taskcontext.getTaskUtilities().reportInputFileDependency(null, file);
			if (!"wait".equals(file.getContent())) {
				taskcontext.getTaskResult(strTaskId("changing"));
			} else {
				taskcontext.getTaskUtilities().startTaskFuture(strTaskId("spawned"), new StringTaskFactory("spawned"));
			}
			return null;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("spawner"), new SpawnerTaskFactory())
				.add(strTaskId("changing"), new ChangingTaskFactory());

		files.putFile(INPUT_FILE_PATH, "nowait");
		runTask("main", main);

		files.putFile(INPUT_FILE_PATH, "wait");
		runTask("main", main);

		files.putFile(INPUT_FILE_PATH, "nowait");
		runTask("main", main);

		files.putFile(INPUT_FILE_PATH, "wait");
		runTask("main", main);
	}

}
