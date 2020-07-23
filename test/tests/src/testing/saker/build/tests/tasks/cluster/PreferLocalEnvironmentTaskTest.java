package testing.saker.build.tests.tasks.cluster;

import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.factories.ClusterNameReturningTaskFactory;
import testing.saker.build.tests.tasks.factories.LocalPreferringClusterNameReturningTaskFactory;

@SakerTest
public class PreferLocalEnvironmentTaskTest extends ClusterBuildTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new ClusterNameReturningTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), DEFAULT_CLUSTER_NAME);

		runTask("preferring", new LocalPreferringClusterNameReturningTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("preferring")), null);
	}

}
