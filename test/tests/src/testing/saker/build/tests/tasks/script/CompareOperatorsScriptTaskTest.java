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
public class CompareOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("la"), 1 < 3);
		assertEquals(result.getTargetTaskResult("lb"), 1 <= 3);
		assertEquals(result.getTargetTaskResult("lc"), 2 < 2);
		assertEquals(result.getTargetTaskResult("ld"), 2 <= 2);
		assertEquals(result.getTargetTaskResult("le"), 3 < 1);
		assertEquals(result.getTargetTaskResult("lf"), 3 <= 1);

		assertEquals(result.getTargetTaskResult("ga"), 1 > 3);
		assertEquals(result.getTargetTaskResult("gb"), 1 >= 3);
		assertEquals(result.getTargetTaskResult("gc"), 2 > 2);
		assertEquals(result.getTargetTaskResult("gd"), 2 >= 2);
		assertEquals(result.getTargetTaskResult("ge"), 3 > 1);
		assertEquals(result.getTargetTaskResult("gf"), 3 >= 1);
	}

}
