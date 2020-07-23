package testing.saker.build.tests.tasks.cluster;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.factories.ClusterNameReturningTaskFactory;
import testing.saker.build.tests.tasks.factories.LocalPreferringClusterNameReturningTaskFactory;

@SakerTest
public class PreferLocalEnvironmentInnerTaskTest extends ClusterBuildTestCase {

	public static class PreferringInnerStarterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.startInnerTask(new LocalPreferringClusterNameReturningTaskFactory(), null).getNext()
					.getResult();
		}
	}

	public static class NonPreferringInnerStarterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.startInnerTask(new ClusterNameReturningTaskFactory(), null).getNext().getResult();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new NonPreferringInnerStarterTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), DEFAULT_CLUSTER_NAME);

		runTask("preferring", new LocalPreferringClusterNameReturningTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("preferring")), null);
	}

	@Override
	protected CollectingTestMetric createMetric() {
		return new CollectingTestMetric() {
			@Override
			public boolean isForceInnerTaskClusterInvocation(Object taskfactory) {
				return true;
			}
		};
	}
}
