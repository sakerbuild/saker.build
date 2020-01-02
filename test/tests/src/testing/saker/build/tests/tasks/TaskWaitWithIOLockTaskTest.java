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
package testing.saker.build.tests.tasks;

import java.util.Objects;

import saker.build.task.TaskContext;
import saker.build.task.exception.IllegalTaskOperationException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskWaitWithIOLockTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class WaiterTask extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.acquireStandardIOLock();
			try {
				return Objects.toString(taskcontext.getTaskResult(strTaskId("str")));
			} finally {
				taskcontext.releaseStandardIOLock();
			}
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory();
		main.add(strTaskId("str"), new StringTaskFactory("content"));
		main.add(strTaskId("waiter"), new WaiterTask());

		assertTaskException(IllegalTaskOperationException.class, () -> runTask("main", main));
	}

}
