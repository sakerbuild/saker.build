package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.UUIDContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.file.StringSakerFile;

@SakerTest
public class TaskAbandonSameOutputDeleteTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class FileOutputTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private ContentDescriptor fileContentDescriptor;

		/**
		 * For {@link Externalizable}.
		 */
		public FileOutputTaskFactory() {
		}

		public FileOutputTaskFactory(ContentDescriptor fileContentDescriptor) {
			this.fileContentDescriptor = fileContentDescriptor;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			SakerFile f = new StringSakerFile("output.txt", "filecontent", fileContentDescriptor);
			taskcontext.getTaskBuildDirectory().add(f);
			f.synchronize();
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, f);
			return null;
		}

		@Override
		public final Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public final void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(fileContentDescriptor);
		}

		@Override
		public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			fileContentDescriptor = (ContentDescriptor) in.readObject();
		}

		@Override
		public final int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public final boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_BUILD_DIRECTORY.resolve("output.txt");

		UUIDContentDescriptor cd = UUIDContentDescriptor.random();
		FileOutputTaskFactory task1 = new FileOutputTaskFactory(cd);
		FileOutputTaskFactory task2 = new FileOutputTaskFactory(cd);

		ChildTaskStarterTaskFactory starter = new ChildTaskStarterTaskFactory().add(strTaskId("subtask1"), task1);
		ChildTaskStarterTaskFactory startermod = new ChildTaskStarterTaskFactory().add(strTaskId("subtask2"), task2);

		runTask("main", starter);
		assertEquals(files.getAllBytes(filepath).toString(), "filecontent");

		runTask("main", startermod);
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("subtask1"));
		if (project != null) {
			//wait execution finalization for project, so abandoned tasks are handled
			project.waitExecutionFinalization();
		}
		assertEquals(files.getAllBytes(filepath).toString(), "filecontent");
	}

}
