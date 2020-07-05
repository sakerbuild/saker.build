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
package testing.saker.build.tests.tasks.mgmt;

import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class DeadlockWithWorkerThreadTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class DeadlockerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			Thread thread = ThreadUtils.startDaemonThread(() -> {
				try {
					Thread.sleep(60 * 1000);
				} catch (InterruptedException e) {
				}
			});
			TaskExecutionDeadlockedException ex = assertException(TaskExecutionDeadlockedException.class,
					() -> taskcontext.getTaskResult(strTaskId("unexist")));
			assertTrue(thread.isAlive());
			thread.interrupt();
			thread.join();
			throw ex;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(TaskExecutionDeadlockedException.class,
				() -> runTask("deadlock", new DeadlockerTaskFactory()));
	}
}
