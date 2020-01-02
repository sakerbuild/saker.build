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
public class BooleanOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), true || false);
		assertEquals(result.getTargetTaskResult("b"), true && false);
		assertEquals(result.getTargetTaskResult("c"), true && true);
		assertEquals(result.getTargetTaskResult("d"), false && false);
		assertEquals(result.getTargetTaskResult("e"), false || false);
		assertEquals(result.getTargetTaskResult("f"), false || true);
		assertEquals(result.getTargetTaskResult("g"), true && true);
		assertEquals(result.getTargetTaskResult("h"), false && true || false);
		assertEquals(result.getTargetTaskResult("i"), true && false || false);
		assertEquals(result.getTargetTaskResult("j"), false && false || true);
	}
}
