package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskDuplicationPredicate;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class EachClusterInnerTaskTest extends ClusterBuildTestCase {
	public static class InnerTaskStarter extends SelfStatelessTaskFactory<String> {
		private static final class InnerTaskDuplicationPredicateImpl implements TaskDuplicationPredicate, Serializable {
			private static final long serialVersionUID = 1L;

			private static final AtomicIntegerFieldUpdater<InnerTaskDuplicationPredicateImpl> AIFU_count = AtomicIntegerFieldUpdater
					.newUpdater(InnerTaskDuplicationPredicateImpl.class, "count");
			@SuppressWarnings("unused")
			private volatile int count;

			public InnerTaskDuplicationPredicateImpl(int count) {
				this.count = count;
			}

			@Override
			public boolean shouldInvokeOnceMore() throws RuntimeException {
				return AIFU_count.getAndDecrement(this) > 0;
			}
		}

		private static final long serialVersionUID = 1L;

		public InnerTaskStarter() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertNotEquals(
					taskcontext.getExecutionContext().getEnvironment().getUserParameters()
							.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM),
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);

			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			//duplicate to all clusters
			execparams.setClusterDuplicateFactor(-1);
			int duplicatecount = 5;
			execparams.setDuplicationPredicateForEachCluster(new InnerTaskDuplicationPredicateImpl(duplicatecount));
			InnerTaskResults<String> innerresults = taskcontext.startInnerTask(new ClusterNameReturningTaskFactory(),
					execparams);
			Collection<String> invokednames = new ArrayList<>();
			while (true) {
				InnerTaskResultHolder<String> n = innerresults.getNext();
				if (n == null) {
					break;
				}
				invokednames.add(n.getResult());
			}
			//2 suitable cluster
			assertEquals(invokednames.size(), 2 * duplicatecount);
			return StringUtils.toStringJoin(null, invokednames);
		}
	}

	private static final class SuitableEnvironmentSelector implements TaskExecutionEnvironmentSelector, Externalizable {
		private static final long serialVersionUID = 1L;

		public SuitableEnvironmentSelector() {
		}

		@Override
		public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
			SuitableClusterEnvironmentProperty envproperty = new SuitableClusterEnvironmentProperty();
			Boolean suitable = environment.getEnvironmentPropertyCurrentValue(envproperty);
			if (!suitable) {
				return null;
			}
			Map<? extends EnvironmentProperty<?>, ?> qualifierProperties = Collections.singletonMap(envproperty,
					suitable);
			return new EnvironmentSelectionResult(qualifierProperties);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	public static class ClusterNameReturningTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new SuitableEnvironmentSelector();
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			String result = taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
			return result;
		}
	}

	public static class SuitableClusterEnvironmentProperty implements EnvironmentProperty<Boolean>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public Boolean getCurrentValue(SakerEnvironment environment) {
			String clustername = environment.getUserParameters().get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
			if (clustername == null) {
				return false;
			}
			return clustername.startsWith("suitable.");
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

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new InnerTaskStarter());

		runTask("main", new InnerTaskStarter());
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ObjectUtils.newTreeSet("suitable.cluster1", "cluster2", "suitable.cluster3")).build();
	}

}
