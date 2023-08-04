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

/**
 * Checks that a warning is emitted if a parameter is set for a build target which has no such parameter.
 */
@SakerTest
public class NoSuchParameterWarningBuildFileTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");

		assertEquals(getMetric().getAllPrintedTaskLines(), setOf(
				"[include]Warning: Target parameterized in file wd:/saker.build has no parameter named: nonexistent",
				"[print]changedSomething"));

		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());
	}

}
