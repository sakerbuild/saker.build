package testing.saker.build.tests.tasks.cluster;

import java.util.NavigableSet;
import java.util.Set;

import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration.MultiBuilder;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

/**
 * test for the case when a task is found to be suitable for a cluster and is being run on it, however the environment
 * selection still runs on other clusters. the selection fails on the others, and it is reported to the execution
 * manager. this report shouldn't cause the abortion of the already running task
 */
@SakerTest
public class StartedOtherUnsuitableTaskTest extends ClusterBuildTestCase {

	public static class RunnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			Thread.sleep(500);
			return null;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new RunnerTaskFactory());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		MultiBuilder builder = EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations());
		for (int i = 0; i < 10; i++) {
			builder.addClusterName("cluster" + (i + 1));
		}
		return builder.build();
	}

}
