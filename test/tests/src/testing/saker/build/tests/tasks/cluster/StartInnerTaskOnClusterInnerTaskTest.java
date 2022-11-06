package testing.saker.build.tests.tasks.cluster;

import java.util.Collections;
import java.util.NavigableSet;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.factories.ClusterNameReturningTaskFactory;

@SakerTest
public class StartInnerTaskOnClusterInnerTaskTest extends ClusterBuildTestCase {

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		public InnerTaskStarter() {
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();
			String cname = environment.getUserParameters().get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
			assertNonNull(cname);

			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			//duplicate to all suitable clusters
			//only the environment that is the same as this is suitable
			execparams.setClusterDuplicateFactor(-1);
			execparams.setAllowedClusterEnvironmentIdentifiers(
					Collections.singleton(environment.getEnvironmentIdentifier()));
			InnerTaskResults<String> innerresults = taskcontext.startInnerTask(new InnerTask(), execparams);
			InnerTaskResultHolder<String> itresult = innerresults.getNext();
			assertNonNull(itresult, "inner task result");
			assertEquals(itresult.getResult(), cname + "|" + null + "|" + cname);
			return cname;
		}
	}

	public static class InnerTask extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();
			String cname = environment.getUserParameters().get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
			assertNonNull(cname);

			InnerTaskExecutionParameters clusterexecparams = new InnerTaskExecutionParameters();
			//duplicate to all suitable clusters
			//only the environment that is the same as this is suitable
			clusterexecparams.setClusterDuplicateFactor(-1);
			clusterexecparams.setAllowedClusterEnvironmentIdentifiers(
					Collections.singleton(environment.getEnvironmentIdentifier()));

			String resultfirst = taskcontext.startInnerTask(new ClusterNameReturningTaskFactory(), null).getNext()
					.getResult();
			String resultsecond = taskcontext.startInnerTask(new ClusterNameReturningTaskFactory(), clusterexecparams)
					.getNext().getResult();
			return cname + "|" + resultfirst + "|" + resultsecond;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new InnerTaskStarter());

		runTask("main", new InnerTaskStarter());
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
