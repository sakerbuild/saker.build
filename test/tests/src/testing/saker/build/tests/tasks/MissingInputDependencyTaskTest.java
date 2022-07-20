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

import java.io.IOException;

import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class MissingInputDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		runTestOnFile(PATH_WORKING_DIRECTORY.resolve("input.txt"));
		runTestOnFile(PATH_WORKING_DIRECTORY.resolve("dir/dirinput.txt"));
	}

	private void runTestOnFile(SakerPath filepath) throws Throwable, AssertionError, IOException, InterruptedException {
		FileStringContentTaskFactory task = new FileStringContentTaskFactory(filepath);

		TaskIdentifier maintaskid = strTaskId("main-" + filepath);
		runTask(maintaskid, task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, null).noRemaining();

		runTask(maintaskid, task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), setOf());

		files.putFile(filepath, "content");
		runTask(maintaskid, task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "content").noRemaining();

		files.putFile(filepath, "modifiedcontent");
		runTask(maintaskid, task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "modifiedcontent").noRemaining();

		if (project != null) {
			//wait the execution finalization, as this caused some bug to surface
			//the SakerFile is not removed properly from the file hierarchy, and retrieving its contents causes an exception
			//the failure was at the next build execution
			project.waitExecutionFinalization();
		}

		files.deleteRecursively(filepath);
		runTask(maintaskid, task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, null).noRemaining();

		files.putFile(filepath, "content2");
		runTask(maintaskid, task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "content2").noRemaining();

		files.deleteRecursively(filepath);
		runTask(maintaskid, task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, null).noRemaining();
	}

}
