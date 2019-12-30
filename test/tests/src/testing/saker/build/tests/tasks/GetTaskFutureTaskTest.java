package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class GetTaskFutureTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class TaskFutureGetter implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier taskId;

		public TaskFutureGetter() {
		}

		public TaskFutureGetter(TaskIdentifier taskId) {
			this.taskId = taskId;
		}

		@Override
		public Task<String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) {
					return Objects.toString(taskcontext.getTaskResult(taskId));
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
			TaskFutureGetter other = (TaskFutureGetter) obj;
			if (taskId == null) {
				if (other.taskId != null)
					return false;
			} else if (!taskId.equals(other.taskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TaskFutureGetter [" + (taskId != null ? "taskId=" + taskId : "") + "]";
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(taskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			taskId = (TaskIdentifier) in.readObject();
		}
	}

	public static class DelayedTaskStarterFactory implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Map<TaskIdentifier, TaskFactory<?>> tasks;

		public DelayedTaskStarterFactory() {
		}

		public DelayedTaskStarterFactory(Map<TaskIdentifier, TaskFactory<?>> tasks) {
			this.tasks = new HashMap<>(tasks);
		}

		@Override
		public Task<Void> createTask(ExecutionContext executioncontext) {
			return new Task<Void>() {

				@Override
				public Void run(TaskContext taskcontext) {
					for (Entry<TaskIdentifier, TaskFactory<?>> entry : tasks.entrySet()) {
						taskcontext.getTaskUtilities().startTaskFuture(entry.getKey(), entry.getValue());
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
					return null;
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((tasks == null) ? 0 : tasks.hashCode());
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
			DelayedTaskStarterFactory other = (DelayedTaskStarterFactory) obj;
			if (tasks == null) {
				if (other.tasks != null)
					return false;
			} else if (!tasks.equals(other.tasks))
				return false;
			return true;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalMap(out, tasks);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			tasks = SerialUtils.readExternalMap(new TreeMap<>(), in);
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		DelayedTaskStarterFactory task = new DelayedTaskStarterFactory(
				TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>())
						.put(strTaskId("1task"), new TaskFutureGetter(strTaskId("2task")))
						.put(strTaskId("2task"), new StringTaskFactory("hello")).build());

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("1task"), "hello")
				.contains(strTaskId("2task"), "hello").contains(strTaskId("main"), null).noRemaining();
	}

}
