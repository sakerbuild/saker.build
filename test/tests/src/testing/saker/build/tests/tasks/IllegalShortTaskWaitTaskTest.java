package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.NavigableSet;
import java.util.TreeSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class IllegalShortTaskWaitTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class NonShortTaskRunnerShortTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			StringTaskFactory stringfac = new StringTaskFactory("result");
			taskcontext.getTaskUtilities().runTaskResult(strTaskId("sub"), stringfac);
			throw fail("Should be unreachable, as waiting for a non short task should throw an exception.");
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return createShortTaskCapabilities();
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

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
	}

	public static class ShortTaskStarterWaiterTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			StringTaskFactory stringfac = new StringTaskFactory("result");
			stringfac.setCapabilities(createShortTaskCapabilities());
			TaskFuture<String> future = taskcontext.getTaskUtilities().startTaskFuture(strTaskId("sub"), stringfac);
			return future.get();
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return createShortTaskCapabilities();
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

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
	}

	public static class NonShortTaskWaiterTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return (String) taskcontext.getTaskResult(strTaskId("nonshort"));
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return createShortTaskCapabilities();
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

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
	}

	protected static NavigableSet<String> createShortTaskCapabilities() {
		TreeSet<String> result = new TreeSet<>();
		result.add(TaskFactory.CAPABILITY_SHORT_TASK);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(IllegalTaskOperationException.class,
				() -> runTask("nonshort", new NonShortTaskRunnerShortTaskFactory()));

		runTask("shortstartwait", new ShortTaskStarterWaiterTaskFactory());
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("shortstartwait"), "result")
				.contains(strTaskId("sub"), "result").noRemaining();

		assertTaskException(IllegalTaskOperationException.class,
				() -> runTask("nswaiterstarter",
						new ChildTaskStarterTaskFactory(new HashMap<>())
								.add(strTaskId("nonshort"), new StringTaskFactory("result"))
								.add(strTaskId("waiter"), new NonShortTaskWaiterTaskFactory())));
	}

}
