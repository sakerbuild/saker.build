package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.UncheckedIOException;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath CONSUMER_FILE_DEPENDENCY = PATH_WORKING_DIRECTORY.resolve("input.txt");

	public static class SpawnerTaskFactory implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String dependentValue;
		private String consumerValue;

		public SpawnerTaskFactory() {
		}

		public SpawnerTaskFactory(String dependentValue, String consumerValue) {
			this.dependentValue = dependentValue;
			this.consumerValue = consumerValue;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(dependentValue);
			out.writeUTF(consumerValue);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependentValue = in.readUTF();
			consumerValue = in.readUTF();
		}

		@Override
		public Task<Void> createTask(ExecutionContext excontext) {
			return new Task<Void>() {
				@Override
				public Void run(TaskContext context) {
					StringTaskFactory str = new StringTaskFactory(dependentValue);
					TaskIdentifier strtaskid = strTaskId("str");
					context.getTaskUtilities().startTaskFuture(strtaskid, str);

					context.getTaskUtilities().startTaskFuture(strTaskId("consumer"),
							new TaskConsumerTaskFactory(consumerValue, strtaskid));
					return null;
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((consumerValue == null) ? 0 : consumerValue.hashCode());
			result = prime * result + ((dependentValue == null) ? 0 : dependentValue.hashCode());
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
			SpawnerTaskFactory other = (SpawnerTaskFactory) obj;
			if (consumerValue == null) {
				if (other.consumerValue != null)
					return false;
			} else if (!consumerValue.equals(other.consumerValue))
				return false;
			if (dependentValue == null) {
				if (other.dependentValue != null)
					return false;
			} else if (!dependentValue.equals(other.dependentValue))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SpawnerTaskFactory [" + (dependentValue != null ? "dependentValue=" + dependentValue + ", " : "")
					+ (consumerValue != null ? "consumerValue=" + consumerValue : "") + "]";
		}

	}

	public static class TaskConsumerTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;
		private TaskIdentifier dependentTaskId;

		public TaskConsumerTaskFactory() {
		}

		public TaskConsumerTaskFactory(String value, TaskFuture<String> dependentTask) {
			this(value, dependentTask.getTaskIdentifier());
		}

		public TaskConsumerTaskFactory(String value, TaskIdentifier dependentTaskId) {
			this.value = value;
			this.dependentTaskId = dependentTaskId;
		}

		@Override
		public Task<String> createTask(ExecutionContext excontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext context) {
					try {
						SakerFile file = context.getTaskUtilities().resolveAtPath(CONSUMER_FILE_DEPENDENCY);
						context.reportInputFileDependency(null, CONSUMER_FILE_DEPENDENCY, file.getContentDescriptor());
						return value + context.getTaskResult(dependentTaskId) + file.getContent();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependentTaskId == null) ? 0 : dependentTaskId.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			TaskConsumerTaskFactory other = (TaskConsumerTaskFactory) obj;
			if (dependentTaskId == null) {
				if (other.dependentTaskId != null)
					return false;
			} else if (!dependentTaskId.equals(other.dependentTaskId))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(value);
			out.writeObject(dependentTaskId);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readUTF();
			dependentTaskId = (TaskIdentifier) in.readObject();
		}

		@Override
		public String toString() {
			return "TaskConsumerTaskFactory [" + (value != null ? "value=" + value + ", " : "")
					+ (dependentTaskId != null ? "dependentTaskId=" + dependentTaskId : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(CONSUMER_FILE_DEPENDENCY, "content");

		runTask("main", new SpawnerTaskFactory("dep", "cons"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "str", "consumer"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("consumer"), "consdepcontent");

		runTask("main", new SpawnerTaskFactory("dep", "cons"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		runTask("main", new SpawnerTaskFactory("dep", "consmod"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "consumer"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("consumer"), "consmoddepcontent");

		files.putFile(CONSUMER_FILE_DEPENDENCY, "modcontent");
		runTask("main", new SpawnerTaskFactory("dep", "consmod"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("consumer"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("consumer"), "consmoddepmodcontent");

		runTask("main", new SpawnerTaskFactory("moddep", "consmod"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "str", "consumer"));

	}

}
