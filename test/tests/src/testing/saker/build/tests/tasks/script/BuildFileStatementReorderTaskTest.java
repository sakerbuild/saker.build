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
package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class BuildFileStatementReorderTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);
		files.putFile(buildfilepath, "build(out i, out j,) {\n $i = 1 \n $j = 2\n }");
		runScriptTask("build");

		files.putFile(buildfilepath, "build(out i, out j,) {\n $j = 2 \n $i = 1 \n }");
		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdResults().size(), 1);

		files.putFile(buildfilepath, "build(out j, out i,) {\n $j = 2 \n $i = 1 \n }");
		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdResults().size(), 1);
	}

}
