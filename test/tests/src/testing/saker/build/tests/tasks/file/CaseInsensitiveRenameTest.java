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
package testing.saker.build.tests.tasks.file;

import java.util.Set;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

//@SakerTest
//test case related to issue #13. Disabled for now as it is not fixed.
public class CaseInsensitiveRenameTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filetxt = PATH_WORKING_DIRECTORY.resolve("file.txt");
		SakerPath FILEtxt = PATH_WORKING_DIRECTORY.resolve("FILE.txt");
		files.putFile(filetxt, "content");

		runTask("main", new FileStringContentTaskFactory(filetxt));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "content");

		if (project != null) {
			project.waitExecutionFinalization();
		}
		files.putFile(FILEtxt, "content");
		runTask("main", new FileStringContentTaskFactory(filetxt));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), null);

	}

	@Override
	protected MemoryFileProvider createMemoryFileProvider(Set<String> roots, UUID filesuuid) {
		return new MemoryFileProvider(roots, filesuuid, SakerPath::compareToIgnoreCase);
	}
}
