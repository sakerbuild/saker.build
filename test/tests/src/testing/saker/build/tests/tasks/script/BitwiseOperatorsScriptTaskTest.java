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
public class BitwiseOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("aa"), 3L & 2L);
		assertEquals(result.getTargetTaskResult("ab"), 3L & -2L);
		assertEquals(result.getTargetTaskResult("ac"), 1L & 2L & 3L);
		assertEquals(result.getTargetTaskResult("ad"), 7L & 10);
		assertEquals(result.getTargetTaskResult("ae"), 10L & 10L);

		assertEquals(result.getTargetTaskResult("oa"), 3L | 2L);
		assertEquals(result.getTargetTaskResult("ob"), 3L | -2L);
		assertEquals(result.getTargetTaskResult("oc"), 1L | 2L | 3L);
		assertEquals(result.getTargetTaskResult("od"), 7L | 10);
		assertEquals(result.getTargetTaskResult("oe"), 10L | 10L);

		assertEquals(result.getTargetTaskResult("xa"), 3L ^ 2L);
		assertEquals(result.getTargetTaskResult("xb"), 3L ^ -2L);
		assertEquals(result.getTargetTaskResult("xc"), 1L ^ 2L ^ 3L);
		assertEquals(result.getTargetTaskResult("xd"), 7L ^ 10);
		assertEquals(result.getTargetTaskResult("xe"), 10L ^ 10L);
	}
}
