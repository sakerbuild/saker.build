package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;
import java.util.Objects;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskDependencyCacheableTaskTest extends CacheableTaskTestCase {

	private static class StringValueOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private String expected;

		public StringValueOutputChangeDetector() {
		}

		public StringValueOutputChangeDetector(String expected) {
			this.expected = expected;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			return !Objects.equals(expected, Objects.toString(taskoutput, null));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(expected);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			expected = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((expected == null) ? 0 : expected.hashCode());
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
			StringValueOutputChangeDetector other = (StringValueOutputChangeDetector) obj;
			if (expected == null) {
				if (other.expected != null)
					return false;
			} else if (!expected.equals(other.expected))
				return false;
			return true;
		}

	}

	private static class DependingTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier dependTaskId;

		public DependingTaskFactory() {
		}

		public DependingTaskFactory(TaskIdentifier dependTaskId) {
			this.dependTaskId = dependTaskId;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			TaskDependencyFuture<?> future = taskcontext.getTaskDependencyFuture(dependTaskId);
			String result = Objects.toString(future.getFinished(), null);
			future.setTaskOutputChangeDetector(new StringValueOutputChangeDetector(result));
			return result;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependTaskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependTaskId = (TaskIdentifier) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependTaskId == null) ? 0 : dependTaskId.hashCode());
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
			DependingTaskFactory other = (DependingTaskFactory) obj;
			if (dependTaskId == null) {
				if (other.dependTaskId != null)
					return false;
			} else if (!dependTaskId.equals(other.dependTaskId))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory();
		main.add(strTaskId("str"), new StringTaskFactory("content"));
		main.add(strTaskId("waiter"), new DependingTaskFactory(strTaskId("str")));

		SequentialChildTaskStarterTaskFactory modmain = new SequentialChildTaskStarterTaskFactory();
		modmain.add(strTaskId("str"), new StringTaskFactory("modified"));
		modmain.add(strTaskId("waiter"), new DependingTaskFactory(strTaskId("str")));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("waiter"), "content");
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("waiter"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("waiter"), "content");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("waiter"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());

		cleanProject();
		runTask("main", modmain);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("waiter"), "modified");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf());
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("waiter"));

		cleanProject();
		runTask("main", modmain);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("waiter"), "modified");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("waiter"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("waiter"), "content");
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("waiter"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
	}

}
