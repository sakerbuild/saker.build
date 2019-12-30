package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.TaskExecutionFailedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class DependencyOnFailedTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class FailerTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String result;

		/**
		 * For {@link Externalizable}.
		 */
		public FailerTaskFactory() {
		}

		public FailerTaskFactory(String result) {
			this.result = result;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			if (result == null) {
				taskcontext.abortExecution(new NullPointerException());
				return null;
			}
			return result;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(result);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			result = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((this.result == null) ? 0 : this.result.hashCode());
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
			FailerTaskFactory other = (FailerTaskFactory) obj;
			if (result == null) {
				if (other.result != null)
					return false;
			} else if (!result.equals(other.result))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "FailerTaskFactory[result=" + result + "]";
		}
	}

	private static class WaiterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				Object taskres = taskcontext.getTaskResult(strTaskId("failer"));
				return Objects.toString(taskres, null);
			} catch (TaskExecutionFailedException e) {
				taskcontext.abortExecution(e);
				return null;
			}
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("failer"), new FailerTaskFactory(null))
				.add(strTaskId("waiter"), new WaiterTaskFactory());

		assertTaskException(NullPointerException.class, () -> runTask("main", main));

		assertTaskException(NullPointerException.class, () -> runTask("main", main));
		assertEmpty(getMetric().getRunTaskIdFactories());

		ChildTaskStarterTaskFactory secondmain = new ChildTaskStarterTaskFactory()
				.add(strTaskId("failer"), new FailerTaskFactory("succeed"))
				.add(strTaskId("waiter"), new WaiterTaskFactory());
		
		runTask("main", secondmain);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("waiter")), "succeed");
		
		runTask("main", secondmain);
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
