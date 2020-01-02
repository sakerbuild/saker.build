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

@SakerTest
public class MissingInputDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("input.txt");

		FileStringContentTaskFactory task = new FileStringContentTaskFactory(filepath);

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), setOf());

		files.putFile(filepath, "content");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "content").noRemaining();

		files.putFile(filepath, "modifiedcontent");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "modifiedcontent").noRemaining();

		files.deleteRecursively(filepath);
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();
	}

}
