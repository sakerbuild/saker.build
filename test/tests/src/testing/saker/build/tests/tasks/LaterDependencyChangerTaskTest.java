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

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;

@SakerTest
public class LaterDependencyChangerTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");
		files.putFile(filepath, "content");

		SequentialChildTaskStarterTaskFactory task = new SequentialChildTaskStarterTaskFactory();
		task.add(strTaskId("reader"), new FileStringContentTaskFactory(filepath));
		task.add(strTaskId("creator"), new StringFileOutputTaskFactory(filepath, "text"));

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "content").contains(strTaskId("creator"), null).noRemaining();
		assertEquals(files.getAllBytes(filepath).toString(), "text");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "text").noRemaining();

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		files.putFile(filepath, "modified");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "modified").contains(strTaskId("creator"), null).noRemaining();
		assertEquals(files.getAllBytes(filepath).toString(), "text");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null)
				.contains(strTaskId("reader"), "text").noRemaining();

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();
	}

}
