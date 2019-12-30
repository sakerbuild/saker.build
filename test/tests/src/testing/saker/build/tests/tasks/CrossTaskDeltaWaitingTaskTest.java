package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class CrossTaskDeltaWaitingTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class StarterWaiterTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier starter;
		private TaskFactory<?> starterFactory;
		private TaskIdentifier dependent;

		public StarterWaiterTaskFactory(TaskIdentifier starter, TaskFactory<?> starterFactory,
				TaskIdentifier dependent) {
			this.starter = starter;
			this.starterFactory = starterFactory;
			this.dependent = dependent;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) throws Exception {
					Object startedresult = taskcontext.getTaskUtilities().runTaskResult(starter, starterFactory);
					Object dep = taskcontext.getTaskResult(dependent);
					return "" + dep + startedresult;
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(starter);
			out.writeObject(starterFactory);
			out.writeObject(dependent);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			starter = (TaskIdentifier) in.readObject();
			starterFactory = (TaskFactory<?>) in.readObject();
			dependent = (TaskIdentifier) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependent == null) ? 0 : dependent.hashCode());
			result = prime * result + ((starter == null) ? 0 : starter.hashCode());
			result = prime * result + ((starterFactory == null) ? 0 : starterFactory.hashCode());
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
			StarterWaiterTaskFactory other = (StarterWaiterTaskFactory) obj;
			if (dependent == null) {
				if (other.dependent != null)
					return false;
			} else if (!dependent.equals(other.dependent))
				return false;
			if (starter == null) {
				if (other.starter != null)
					return false;
			} else if (!starter.equals(other.starter))
				return false;
			if (starterFactory == null) {
				if (other.starterFactory != null)
					return false;
			} else if (!starterFactory.equals(other.starterFactory))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory root = new ChildTaskStarterTaskFactory();
		root.add(strTaskId("left"), new StarterWaiterTaskFactory(strTaskId("leftsub"), new StringTaskFactory("lsv"),
				strTaskId("rightsub")));
		root.add(strTaskId("right"), new StarterWaiterTaskFactory(strTaskId("rightsub"), new StringTaskFactory("rsv"),
				strTaskId("leftsub")));

		assertTaskException(TaskExecutionDeadlockedException.class, () -> runTask("root", root));
	}

}
