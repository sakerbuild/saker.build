package testing.saker.build.tests.tasks;

import java.io.Externalizable;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InnerTaskFailTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public InnerTaskStarter() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.startInnerTask(new FailingTaskFactory(), null);
			return null;
		}
	}

	public static class FailingTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			throw new UnsupportedOperationException("fail");
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(UnsupportedOperationException.class, () -> runTask("main", new InnerTaskStarter()));
	}

}
