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

import java.util.Collections;
import java.util.NavigableSet;
import java.util.Set;
import java.util.UUID;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.InnerTaskInitializationException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

/**
 * Similar to {@link SameClusterInvocationInnerTaskTest}, but the inner task factory fails to transfer.
 * <p>
 * Checks that the build system properly shuts down when all the allowed environments fail.
 */
@SakerTest
public class ClusterFailAllowedEnvIdentifierInnerTaskTest extends ClusterBuildTestCase {
	public static class FailingInnerTaskStarter extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		private transient UUID clusterEnvId;

		public FailingInnerTaskStarter() {
		}

		public FailingInnerTaskStarter(UUID clusterEnvId) {
			this.clusterEnvId = clusterEnvId;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertNonNull(clusterEnvId, "cluster env id is null");

			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			//duplicate to all suitable clusters
			execparams.setClusterDuplicateFactor(-1);
			execparams.setAllowedClusterEnvironmentIdentifiers(Collections.singleton(clusterEnvId));
			InnerTaskResults<String> innerresults = taskcontext
					.startInnerTask(new TransferFailClusterNameReturningTaskFactory(), execparams);
			throw assertException(InnerTaskInitializationException.class, () -> innerresults.getNext());
		}
	}

	//not serializable
	private static class TransferFailClusterNameReturningTaskFactory implements TaskFactory<String>, Task<String> {
		public TransferFailClusterNameReturningTaskFactory() {
			super();
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			String result = taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
			System.out.println("TransferFailClusterNameReturningTaskFactory.run() " + result);
			return result;
		}

		@Override
		public final Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public final int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public final boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		UUID envid = clusterEnvironments.get("cluster1").getEnvironmentIdentifier();
		FailingInnerTaskStarter taskfactory = new FailingInnerTaskStarter(envid);
		assertTaskException(InnerTaskInitializationException.class, () -> runTask("main", taskfactory));
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ObjectUtils.newTreeSet("cluster1", "cluster2", "cluster3")).build();
	}
}
