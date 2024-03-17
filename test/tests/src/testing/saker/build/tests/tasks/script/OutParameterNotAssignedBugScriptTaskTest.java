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

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class OutParameterNotAssignedBugScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"), "build(\nout x\nout y\n) { $y = 4; }");

		CombinedTargetTaskResult res = runScriptTask("build");
		//variable was not assigned, so the task result is not found
		assertException(IllegalArgumentException.class, () -> res.getTargetTaskResult("x"));
		assertEquals(res.getTargetTaskResult("y"), 4L);

		System.out.println("OutParameterNotAssignedBugScriptTaskTest.runTestImpl() RUN 2");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"), "build(\nout x\n) {\n $x = 3;\n }");
		runScriptTask("build");
	}

}
