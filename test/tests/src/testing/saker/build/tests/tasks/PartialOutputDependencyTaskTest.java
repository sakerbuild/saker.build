package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class PartialOutputDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath FIRST_FILE = PATH_WORKING_DIRECTORY.resolve("first.txt");
	private static final SakerPath SECOND_FILE = PATH_WORKING_DIRECTORY.resolve("second.txt");

	private static class TheOutput implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected String first;
		protected String second;

		public TheOutput() {
		}

		public TheOutput(String first, String second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(first);
			out.writeUTF(second);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			first = in.readUTF();
			second = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((first == null) ? 0 : first.hashCode());
			result = prime * result + ((second == null) ? 0 : second.hashCode());
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
			TheOutput other = (TheOutput) obj;
			if (first == null) {
				if (other.first != null)
					return false;
			} else if (!first.equals(other.first))
				return false;
			if (second == null) {
				if (other.second != null)
					return false;
			} else if (!second.equals(other.second))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TheOutput[" + (first != null ? "first=" + first + ", " : "")
					+ (second != null ? "second=" + second : "") + "]";
		}
	}

	private static class CompositeFileTaskFactory implements TaskFactory<TheOutput>, Task<TheOutput>, Externalizable {
		private static final long serialVersionUID = 1L;

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

		@Override
		public TheOutput run(TaskContext taskcontext) throws Exception {
			SakerFile first = taskcontext.getTaskUtilities().resolveAtPath(FIRST_FILE);
			SakerFile second = taskcontext.getTaskUtilities().resolveAtPath(SECOND_FILE);
			taskcontext.getTaskUtilities().reportInputFileDependency(null, first);
			taskcontext.getTaskUtilities().reportInputFileDependency(null, second);
			return new TheOutput(first.getContent(), second.getContent());
		}

		@Override
		public Task<? extends TheOutput> createTask(ExecutionContext executioncontext) {
			return this;
		}
	}

	private static class FirstConsumerTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

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

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			TaskFuture<?> future = taskcontext.getTaskFuture(strTaskId("outputer"));
			TaskDependencyFuture<?> tresult = future.asDependencyFuture();
			TheOutput output = (TheOutput) tresult.get();
			tresult.setTaskOutputChangeDetector(new FirstOutputTaskOutputChangeDetector(output.first));
			return output.first;
		}
	}

	private static class FirstOutputTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;
		private String firstOutput;

		public FirstOutputTaskOutputChangeDetector() {
		}

		public FirstOutputTaskOutputChangeDetector(String firstOutput) {
			this.firstOutput = firstOutput;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			TheOutput theoutput = (TheOutput) taskoutput;
			return !Objects.equals(firstOutput, theoutput.first);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(firstOutput);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			firstOutput = (String) in.readObject();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("outputer"), new CompositeFileTaskFactory())
				.add(strTaskId("consumer"), new FirstConsumerTaskFactory());

		files.putFile(FIRST_FILE, "first");
		files.putFile(SECOND_FILE, "second");
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("consumer"), "first")
				.contains(strTaskId("outputer"), new TheOutput("first", "second"));

		files.putFile(SECOND_FILE, "secondmod");
		runTask("main", main);
		System.out.println("PartialOutputDependencyTaskTest.runTestImpl() " + getMetric().getRunTaskIdResults());
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("outputer"), new TheOutput("first", "secondmod"))
				.noRemaining();

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		files.putFile(FIRST_FILE, "firstmod");
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("consumer"), "firstmod")
				.contains(strTaskId("outputer"), new TheOutput("firstmod", "secondmod")).noRemaining();
	}

}
