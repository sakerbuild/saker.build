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

import java.io.Externalizable;
import java.util.NavigableSet;

import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class ClusterDeadlockTaskTest extends ClusterBuildTestCase {
	private static class ClusterWaiterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public ClusterWaiterTaskFactory() {
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertEquals(taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM), DEFAULT_CLUSTER_NAME);
			return taskcontext.getTaskResult(strTaskId("non-existent")).toString();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory starter = new ChildTaskStarterTaskFactory();
		starter.add(strTaskId("dispatched"), new StringTaskFactory("x")
				.setCapabilities(ObjectUtils.newTreeSet(TaskFactory.CAPABILITY_REMOTE_DISPATCHABLE)));
		starter.add(strTaskId("localwaiter"), new TaskWaitingTaskFactory(strTaskId("non-existent")));

		//if clusters are configured to be used, but are not used, we should be able to detect deadlocks
		assertTaskException(TaskExecutionDeadlockedException.class,
				() -> runTask("simpledeadlock", new TaskWaitingTaskFactory(strTaskId("simple-non-existent"))));
		//clusters are configured, and we use it, but not by the task that deadlocks.
		//we should be able to detect the deadlock.
		assertTaskException(TaskExecutionDeadlockedException.class, () -> runTask("main", starter));

		assertTaskException(TaskExecutionDeadlockedException.class, () -> runTask("main", new ClusterWaiterTaskFactory()));
	}

}
