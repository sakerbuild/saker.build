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

import saker.build.file.path.SakerPath;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class FileModifyTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");

		FileStringContentTaskFactory filetask = new FileStringContentTaskFactory(filepath);
		files.putFile(filepath, "hello");

		runTask("file", filetask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));
		runTask("file", filetask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(filepath, "world");
		runTask("file", filetask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));

		ChildTaskStarterTaskFactory childer = new ChildTaskStarterTaskFactory(TestUtils
				.mapBuilder(new HashMap<TaskIdentifier, TaskFactory<?>>()).put(strTaskId("file"), filetask).build());

		runTask("childer", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("childer"));

		files.putFile(filepath, "subworld");
		runTask("childer", childer);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));
	}

}
