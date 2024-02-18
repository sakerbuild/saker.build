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

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class FailedTaskRerunTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class FailerException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private static class AborterException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private static boolean shouldThrow = false;
	private static boolean shouldAbort = false;

	private static class FailerTaskFactory extends StringTaskFactory {
		private static final long serialVersionUID = 1L;

		public FailerTaskFactory() {
			super();
		}

		public FailerTaskFactory(String result) {
			super(result);
		}

		@Override
		public String run(TaskContext context) throws Exception {
			if (shouldThrow) {
				throw new FailerException();
			}
			if (shouldAbort) {
				context.abortExecution(new AborterException());
				return null;
			}
			return super.run(context);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		//reset vars
		shouldAbort = false;
		shouldThrow = false;

		runTask("main", new FailerTaskFactory("result"));
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main"));

		shouldThrow = true;
		assertTaskException(FailerException.class, () -> runTask("main", new FailerTaskFactory("changed")));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		//try running again, it should be ran and fail again
		assertTaskException(FailerException.class, () -> runTask("main", new FailerTaskFactory("changed")));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		//previously failed task should not rerun, as it has not changed since the previous successful run
		shouldThrow = false;
		runTask("main", new FailerTaskFactory("result"));
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());

		runTask("main", new FailerTaskFactory("resultx"));
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main"));

		shouldAbort = true;
		assertTaskException(AborterException.class, () -> runTask("main", new FailerTaskFactory("aborter")));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		//no re-run as it was due to abortion, and the task factory didn't change
		assertTaskException(AborterException.class, () -> runTask("main", new FailerTaskFactory("aborter")));
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());

		//rerun, because the task changed
		assertTaskException(AborterException.class, () -> runTask("main", new FailerTaskFactory("aborter2")));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
	}

}
