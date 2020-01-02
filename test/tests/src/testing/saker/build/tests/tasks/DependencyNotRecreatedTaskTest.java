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

import saker.build.task.exception.TaskExecutionDeadlockedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class DependencyNotRecreatedTaskTest extends CollectingMetricEnvironmentTestCase {
	//this test can take more amount of time than usual, as it can include a polling wait time for the deadlock test

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory starter = new ChildTaskStarterTaskFactory();
		starter.add(strTaskId("first"), new StringTaskFactory("firstres"));
		starter.add(strTaskId("second"), new StringTaskFactory("secondres"));
		starter.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("second")));

		runTask(strTaskId("main"), starter);

		runTask(strTaskId("main"), starter);
		assertEmpty(getMetric().getRunTaskIdFactories().keySet());

		{
			ChildTaskStarterTaskFactory changedstarter = new ChildTaskStarterTaskFactory();
			changedstarter.add(strTaskId("first"), new StringTaskFactory("firstres"));
			changedstarter.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("second")));
			assertTaskException(TaskExecutionDeadlockedException.class,
					() -> runTask(strTaskId("main"), changedstarter));
		}

		runTask(strTaskId("main"), starter);

		{
			ChildTaskStarterTaskFactory changedstarter = new ChildTaskStarterTaskFactory();
			changedstarter.add(strTaskId("first"), new StringTaskFactory("firstres"));
			changedstarter.add(strTaskId("replacedstarter"),
					new ChildTaskStarterTaskFactory().add(strTaskId("second"), new StringTaskFactory("secondres")));
			changedstarter.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("second")));
			runTask(strTaskId("main"), changedstarter);
			//waiter might not be rerun, as the task with the id "second" is not actually changed, but moved
			//    the following can happen
			//    1. run main -> start first, replacedstarter, waiter -> replacedstarted finish & second finish -> waiter dependencies unchanged
			//    2. run main -> start first, replacedstarter, waiter -> waiter dependencies are changed, as second is not restarted by main, so waiter is run 
			assertTrue(getMetric().getRunTaskIdFactories().keySet()
					.containsAll(strTaskIdSetOf("main", "replacedstarter")));
		}

	}
}
