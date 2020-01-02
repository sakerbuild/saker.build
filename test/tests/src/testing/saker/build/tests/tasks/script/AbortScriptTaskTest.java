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
public class AbortScriptTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		String msg;

		msg = assertTaskException("saker.build.internal.scripting.language.exc.BuildAbortedException",
				() -> runScriptTask("build")).getMessage();
		assertEquals(msg, "AbortMessage");
		assertNotEmpty(getMetric().getRunTaskIdFactories());

		//make sure that when the build is rerun, the abort task is not reinvoked, as no deltas for it have changed
		msg = assertTaskException("saker.build.internal.scripting.language.exc.BuildAbortedException",
				() -> runScriptTask("build")).getMessage();
		assertEquals(msg, "AbortMessage");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}
}
