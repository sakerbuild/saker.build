package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskConcatTaskFactory;

@SakerTest
public class IntermediateTaskDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUT_FILE = PATH_WORKING_DIRECTORY.resolve("input.txt");

	public static class ComposerTaskFactory implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		public ComposerTaskFactory() {
		}

		@Override
		public Task<Void> createTask(ExecutionContext excontext) {
			return new Task<Void>() {
				@Override
				public Void run(TaskContext taskcontext) {
					FileStringContentTaskFactory filetaskfactory = new FileStringContentTaskFactory(INPUT_FILE);
					TaskFuture<String> filetask = taskcontext.getTaskUtilities().startTaskFuture(strTaskId("file"),
							filetaskfactory);
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId("concat"),
							new StringTaskConcatTaskFactory("concatstr", filetask));
					return null;
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(INPUT_FILE, "content");

		ComposerTaskFactory factory = new ComposerTaskFactory();

		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "file", "concat"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("file"), "content").contains(strTaskId("concat"),
				"concatstrcontent");

		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(INPUT_FILE, "modcontent");
		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file", "concat"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("file"), "modcontent")
				.contains(strTaskId("concat"), "concatstrmodcontent");
	}

}
