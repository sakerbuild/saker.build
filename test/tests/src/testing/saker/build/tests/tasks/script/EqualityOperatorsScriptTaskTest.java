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

import java.util.Arrays;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class EqualityOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), 1 == 3);
		assertEquals(result.getTargetTaskResult("b"), 1 != 3);
		assertEquals(result.getTargetTaskResult("c"), 1 == 1);
		assertEquals(result.getTargetTaskResult("d"), 1 != -2);
		assertEquals(result.getTargetTaskResult("e"), 1 == 10);
		assertEquals(result.getTargetTaskResult("f"), 10 == 10);
		assertEquals(result.getTargetTaskResult("g"), 10 == 10);
		assertEquals(result.getTargetTaskResult("h"), 10 != 10);

		assertEquals(result.getTargetTaskResult("i"), 1 == 1 == true);
		assertEquals(result.getTargetTaskResult("j"), 1 == 2 == false);

		assertEquals(result.getTargetTaskResult("l"), true);
		assertEquals(result.getTargetTaskResult("m"), true);
		assertEquals(result.getTargetTaskResult("num"), true);

		assertEquals(result.getTargetTaskResult("alltrues"), Arrays.asList(true, true, true, true, true, true));
	}
}
