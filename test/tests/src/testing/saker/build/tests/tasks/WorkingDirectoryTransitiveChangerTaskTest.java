package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class WorkingDirectoryTransitiveChangerTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class WorkingDirChangerTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath workingDir;
		private TaskIdentifier taskId;
		private TaskFactory<?> factory;

		public WorkingDirChangerTaskFactory() {
		}

		public WorkingDirChangerTaskFactory(SakerPath workingDir, TaskIdentifier taskid, TaskFactory<?> factory) {
			this.workingDir = workingDir;
			this.taskId = taskid;
			this.factory = factory;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			TaskExecutionParameters parameters = new TaskExecutionParameters();
			parameters.setWorkingDirectory(workingDir);
			taskcontext.startTask(taskId, factory, parameters);
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(workingDir);
			out.writeObject(taskId);
			out.writeObject(factory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			workingDir = (SakerPath) in.readObject();
			taskId = (TaskIdentifier) in.readObject();
			factory = (TaskFactory<?>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((factory == null) ? 0 : factory.hashCode());
			result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
			result = prime * result + ((workingDir == null) ? 0 : workingDir.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			WorkingDirChangerTaskFactory other = (WorkingDirChangerTaskFactory) obj;
			if (factory == null) {
				if (other.factory != null)
					return false;
			} else if (!factory.equals(other.factory))
				return false;
			if (taskId == null) {
				if (other.taskId != null)
					return false;
			} else if (!taskId.equals(other.taskId))
				return false;
			if (workingDir == null) {
				if (other.workingDir != null)
					return false;
			} else if (!workingDir.equals(other.workingDir))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("first/second/file.txt");
		files.putFile(filepath, "content");
		SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory();
		main.add(strTaskId("first"),
				new WorkingDirChangerTaskFactory(SakerPath.valueOf("first"), strTaskId("second"),
						new WorkingDirChangerTaskFactory(SakerPath.valueOf("second"), strTaskId("file"),
								new FileStringContentTaskFactory(SakerPath.valueOf("file.txt")))));
		main.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("file")));

		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("file")), "content");
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("waiter")), "content");

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(filepath, "modified");
		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("file")), "modified");
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("waiter")), "modified");
	}

}
