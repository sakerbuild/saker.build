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
import testing.saker.build.tests.TestUtils;

@SakerTest
public class OmitCommasScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		for (int i = 1; i <= 10; i++) {
			assertEquals(res.getTargetTaskResult("o" + i), (long) i);
		}
		assertEquals(res.getTargetTaskResult("i1"), "x");

		assertEquals(res.getTargetTaskResult("l1"), listOf(1L, 2L, 3L, 4L));
		assertEquals(res.getTargetTaskResult("m1"),
				TestUtils.hashMapBuilder().put("k1", "v1").put("k2", "v2").put("k3", "v3").put("k4", "v4").build());
	}

}
