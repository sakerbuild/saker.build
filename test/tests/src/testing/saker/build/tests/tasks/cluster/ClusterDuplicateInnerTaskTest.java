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

import java.util.HashSet;
import java.util.Set;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.factories.ClusterNameReturningTaskFactory;

@SakerTest
public class ClusterDuplicateInnerTaskTest extends ClusterBuildTestCase {
	public static class InnerTaskStarter extends SelfStatelessTaskFactory<String> {
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
			InnerTaskResults<String> innerresults = taskcontext.startInnerTask(new ClusterNameReturningTaskFactory(),
					execparams);
			Set<String> invokednames = new HashSet<>();
			while (true) {
				InnerTaskResultHolder<String> n = innerresults.getNext();
				if (n == null) {
					break;
				}
				invokednames.add(n.getResult());
			}
			assertEquals(invokednames, setOf("cluster1", "cluster2", "cluster3", null));
			return StringUtils.toStringJoin(null, invokednames);
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
