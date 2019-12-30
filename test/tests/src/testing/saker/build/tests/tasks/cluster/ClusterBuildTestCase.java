package testing.saker.build.tests.tasks.cluster;

import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;

public abstract class ClusterBuildTestCase extends CollectingMetricEnvironmentTestCase {
	public static final String DEFAULT_CLUSTER_NAME = "cluster";

	@Override
	public void executeRunning() throws Exception {
		CollectingTestMetric tm = new CollectingTestMetric();
		TestFlag.set(tm);
		super.executeRunning();
		assertEmpty(tm.getLoadedClassPaths());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ImmutableUtils.singletonSet(DEFAULT_CLUSTER_NAME)).build();
	}

}
