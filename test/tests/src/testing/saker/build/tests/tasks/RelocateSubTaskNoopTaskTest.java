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

import java.util.HashMap;

import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class RelocateSubTaskNoopTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory hellotask = new StringTaskFactory("hello");
		StringTaskFactory worldtask = new StringTaskFactory("world");

		ChildTaskStarterTaskFactory subtask = new ChildTaskStarterTaskFactory().add(strTaskId("hello"), hellotask)
				.add(strTaskId("world"), worldtask);
		ChildTaskStarterTaskFactory rootf = new ChildTaskStarterTaskFactory().add(strTaskId("sub"), subtask);

		runTask("main", rootf);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "hello", "world", "sub"));

		ChildTaskStarterTaskFactory changedroot = new ChildTaskStarterTaskFactory(
				TestUtils.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>()).put(strTaskId("hello"), hellotask)
						.put(strTaskId("world"), worldtask).build());

		runTask("main", changedroot);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		runTask("hello", hellotask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
	}

}
