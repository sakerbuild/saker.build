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
public class MultiplyOperatorScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), 1L * 3L);
		assertEquals(result.getTargetTaskResult("b"), 1L * -3L);
		assertEquals(result.getTargetTaskResult("c"), 1.2d * 3.4d);
		assertEquals(result.getTargetTaskResult("d"), -1.2d * -3.4d * 5.6d);
		assertEquals(result.getTargetTaskResult("e"), 1L * 10L);
		assertEquals(result.getTargetTaskResult("f"), 10L * 10L);
	}
}
