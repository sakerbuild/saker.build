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
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class DependencyDatabaseDeleteCleanTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory main = new StringTaskFactory("str");
		runTask("main", main);

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		if (project != null) {
			project.waitExecutionFinalization();
		}
		SakerPath databasepath = PATH_BUILD_DIRECTORY.resolve("dependencies.map");
		assertTrue(files.getFileAttributes(databasepath).isRegularFile());
		files.delete(databasepath);

		runTask("main", main);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
	}

}
