package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class TaskStartingCacheableTaskTest extends CacheableTaskTestCase {

	private static class StartingTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String content;

		public StartingTaskFactory() {
		}

		public StartingTaskFactory(String content) {
			this.content = content;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("started"), new StringTaskFactory(content));
			return content;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(content);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			content = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((content == null) ? 0 : content.hashCode());
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
			StartingTaskFactory other = (StartingTaskFactory) obj;
			if (content == null) {
				if (other.content != null)
					return false;
			} else if (!content.equals(other.content))
				return false;
			return true;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("starting"), new StartingTaskFactory("content"))
				.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("started")));
		ChildTaskStarterTaskFactory modmain = new ChildTaskStarterTaskFactory()
				.add(strTaskId("starting"), new StartingTaskFactory("modified"))
				.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("started")));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("starting"), "content")
				.contains(strTaskId("started"), "content");
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("starting"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("starting"), "content")
				.contains(strTaskId("started"), "content");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("starting"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());

		cleanProject();
		runTask("main", modmain);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("starting"), "modified")
				.contains(strTaskId("started"), "modified");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf());
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("starting"));

		cleanProject();
		runTask("main", modmain);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("starting"), "modified")
				.contains(strTaskId("started"), "modified");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("starting"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
	}

}
