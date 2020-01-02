/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package testing.saker.build.tests.tasks.cluster;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskDuplicationPredicate;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class CoordinatorPredicateInnerTaskTest extends ClusterBuildTestCase {
	public static class InnerTaskStarter extends SelfStatelessTaskFactory<String> {
		private static final class InnerTaskDuplicationPredicateImpl implements TaskDuplicationPredicate, Serializable {
			private static final long serialVersionUID = 1L;

			private static final AtomicIntegerFieldUpdater<InnerTaskDuplicationPredicateImpl> AIFU_count = AtomicIntegerFieldUpdater
					.newUpdater(InnerTaskDuplicationPredicateImpl.class, "count");
			@SuppressWarnings("unused")
			private volatile int count;

			private transient boolean notInvokable = false;

			public InnerTaskDuplicationPredicateImpl(int count) {
				this.count = count;
			}

			public void setNotInvokable(boolean notInvokable) {
				this.notInvokable = notInvokable;
			}

			@Override
			public boolean shouldInvokeOnceMore() throws RuntimeException {
				if (notInvokable) {
					throw new UnsupportedOperationException();
				}
				System.out.println(
						"CoordinatorPredicateInnerTaskTest.InnerTaskStarter.InnerTaskDuplicationPredicateImpl.shouldInvokeOnceMore()");
				return AIFU_count.getAndDecrement(this) > 0;
			}
		}

		private static final long serialVersionUID = 1L;

		public InnerTaskStarter() {
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE, CAPABILITY_INNER_TASKS_COMPUTATIONAL);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertNonNull(taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM));

			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			//duplicate to all clusters
			execparams.setClusterDuplicateFactor(-1);
			int duplicatecount = 100;
			InnerTaskDuplicationPredicateImpl duppredicate = new InnerTaskDuplicationPredicateImpl(duplicatecount);
			duppredicate.setNotInvokable(true);
			execparams.setDuplicationPredicateOnCoordinator(duppredicate);

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
			assertEquals(invokednames.size(), duplicatecount);
			return StringUtils.toStringJoin(null, invokednames);
		}
	}

	public static class ClusterNameReturningTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			String result = taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
			return result;
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
				.setClusterNames(ObjectUtils.newTreeSet("cluster1", "cluster2", "cluster3")).build();
	}

}
