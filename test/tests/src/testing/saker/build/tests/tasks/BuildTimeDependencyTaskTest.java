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
import testing.saker.build.tests.tasks.factories.BuildTimeStringTaskFactory;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class BuildTimeDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		BuildTimeStringTaskFactory bttask = new BuildTimeStringTaskFactory();

		ChildTaskStarterTaskFactory childer = new ChildTaskStarterTaskFactory(TestUtils
				.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>()).put(strTaskId("time"), bttask).build());

		runTask("time", bttask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time"));

		Thread.sleep(20);
		runTask("time", bttask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time"));

		Thread.sleep(20);
		runTask("main", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time", "main"));

		Thread.sleep(20);
		runTask("main", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("time"));
	}

}
