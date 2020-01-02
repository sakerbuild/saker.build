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

/**
 * This test is for a specific bug where modifying a variable in an included build file caused the caller to be
 * deadlocked if the result was used in a for-each control structure.
 */
@SakerTest
public class ForeachDeadlockingBuildFileTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath testbuildpath = PATH_WORKING_DIRECTORY.resolve("test.build");
		SakerPath otherbuildpath = PATH_WORKING_DIRECTORY.resolve("other.build");
		runScriptTask("test", testbuildpath);

		//with this modification, the execution should not deadlock
		files.putFile(otherbuildpath,
				files.getAllBytes(otherbuildpath).toString().replace("theelemvalue", "\"{$Input}\""));
		runScriptTask("test", testbuildpath);

	}

}
