package testing.saker.build.tests.tasks;

import java.util.Collections;
import java.util.Set;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ShortFailingTaskStarterTest extends CollectingMetricEnvironmentTestCase {

	public static class ShortFailerTask extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			throw new UnsupportedOperationException();
		}

		@Override
		@SuppressWarnings("deprecation")
		public Set<String> getCapabilities() {
			return Collections.singleton(CAPABILITY_SHORT_TASK);
		}
	}

	public static class StarterTask extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			try {
				taskcontext.startTask(strTaskId("failer"), new ShortFailerTask(), null);
			} catch (Exception e) {
				throw new AssertionError(e);
			}
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(UnsupportedOperationException.class, () -> runTask("main", new StarterTask()));
	}

}
