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
import saker.build.task.TaskResultCollection;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class ForwardingTaskResultTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class ForwardingTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier forwardedTaskId;

		/**
		 * For {@link Externalizable}.
		 */
		public ForwardingTaskFactory() {
		}

		public ForwardingTaskFactory(TaskIdentifier forwardedTaskId) {
			this.forwardedTaskId = forwardedTaskId;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
//			taskcontext.forwardTaskResult(forwardedTaskId);
			return new SimpleStructuredObjectTaskResult(forwardedTaskId);
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(forwardedTaskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			forwardedTaskId = (TaskIdentifier) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((forwardedTaskId == null) ? 0 : forwardedTaskId.hashCode());
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
			ForwardingTaskFactory other = (ForwardingTaskFactory) obj;
			if (forwardedTaskId == null) {
				if (other.forwardedTaskId != null)
					return false;
			} else if (!forwardedTaskId.equals(other.forwardedTaskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ForwardingTaskFactory [" + (forwardedTaskId != null ? "forwardedTaskId=" + forwardedTaskId : "")
					+ "]";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory();
		main.add(strTaskId("str"), new FileStringContentTaskFactory(filepath));
		main.add(strTaskId("forwarder"), new ForwardingTaskFactory(strTaskId("str")));
		main.add(strTaskId("consumer"), new TaskWaitingTaskFactory(strTaskId("forwarder")));

		files.putFile(filepath, "content");

		TaskResultCollection res;
		res = runTask("main", main);
		assertEquals(StructuredTaskResult.getActualTaskResult(strTaskId("str"), res), "content");
		assertEquals(StructuredTaskResult.getActualTaskResult(strTaskId("consumer"), res), "content");

		res = runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		files.putFile(filepath, "modcontent");
		res = runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("str"));
		assertEquals(StructuredTaskResult.getActualTaskResult(strTaskId("str"), res), "modcontent");
		assertEquals(StructuredTaskResult.getActualTaskResult(strTaskId("consumer"), res), "modcontent");
//		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("str"), "modcontent").contains(strTaskId("consumer"), "modcontent").noRemaining();
	}
}
