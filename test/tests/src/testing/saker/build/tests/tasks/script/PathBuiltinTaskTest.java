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
public class PathBuiltinTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("wd"), PATH_WORKING_DIRECTORY);
		assertEquals(res.getTargetTaskResult("rel"), PATH_WORKING_DIRECTORY.resolve("abc"));
		assertEquals(res.getTargetTaskResult("abs"), SakerPath.valueOf("/home/user"));
		assertEquals(res.getTargetTaskResult("sec"), PATH_WORKING_DIRECTORY.resolve("dir/sec"));
		assertEquals(res.getTargetTaskResult("secabs"), SakerPath.valueOf("/sec/abs"));

		assertEquals(res.getTargetTaskResult("varpath"), PATH_WORKING_DIRECTORY.resolve("varpath"));
	}

}
