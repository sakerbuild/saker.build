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
package testing.saker.build.tests.tasks.bugs;

import saker.build.task.exception.TaskExecutionDeadlockedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class RunTaskDeadlockNotDetectedTaskTest extends CollectingMetricEnvironmentTestCase {
	//the following code doesn't finish because the runTask invocation didn't add a waiter thread
	//so deadlock wasn't detected

	@Override
	protected void runTestImpl() throws Throwable {
		{ // this is the minimal example that reproduces the issue
			SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory()
					.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("nonexist")));
			assertTaskException(TaskExecutionDeadlockedException.class, () -> runTask("main", main));
		}

		//this is the original code that was used when the issue was first encountered
		System.out.println("DeadlockNotDetectedTaskTest.runTestImpl() start first");
		{
			ChildTaskStarterTaskFactory substarter = new ChildTaskStarterTaskFactory().add(strTaskId("str"),
					new StringTaskFactory("123"));
			ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
					.add(strTaskId("substarter"), substarter)
					.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("str")));
			runTask("main", main);
		}
		System.out.println("DeadlockNotDetectedTaskTest.runTestImpl() start second time");
		{
			ChildTaskStarterTaskFactory substarter = new ChildTaskStarterTaskFactory().add(strTaskId("strx"),
					new StringTaskFactory("456"));
			SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory()
					.add(strTaskId("substarter"), substarter)
					.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("str")))
					.add(strTaskId("str"), new StringTaskFactory("123456"));
			//this should deadlock, as "str" is started after the waiting for it starts
			assertTaskException(TaskExecutionDeadlockedException.class, () -> runTask("main", main));
		}
	}

}
