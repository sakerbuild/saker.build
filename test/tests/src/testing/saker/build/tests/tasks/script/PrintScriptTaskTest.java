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
public class PrintScriptTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("[print]hello"));

		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("[print]hello"));

		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"),
				files.getAllBytes(PATH_WORKING_DIRECTORY.resolve("saker.build")).toString().replace("hello", "mod"));
		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("[print]mod"));
	}
}
