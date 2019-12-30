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
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SimpleDeadlockTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class DeadlockerTaskFactory implements TaskFactory<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new Task<Object>() {

				@Override
				public Object run(TaskContext taskcontext) throws Exception {
					taskcontext.getTaskResult(strTaskId("unexist"));
					return "result";
				}
			};
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
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(TaskExecutionDeadlockedException.class,
				() -> runTask("deadlock", new DeadlockerTaskFactory()));
	}
}
