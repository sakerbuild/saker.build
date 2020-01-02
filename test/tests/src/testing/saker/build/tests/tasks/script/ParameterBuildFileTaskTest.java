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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

@SakerTest
public class ParameterBuildFileTaskTest extends VariablesMetricEnvironmentTestCase {
	private String input = "abc";

	@Override
	protected Map<String, ?> getTaskVariables() {
		if (input == null) {
			return Collections.emptyNavigableMap();
		}
		return TestUtils.mapBuilder(new TreeMap<String, Object>()).put("test.TestInput", input).build();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		this.input = "abc";

		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "modifiedbaseinputabc");
		assertEquals(res.getTargetTaskResult("fixedres"), "modifiedfixed");
		assertEquals(res.getTargetTaskResult("defres"), "modifieddefval");
		assertEquals(res.getTargetTaskResult("localres"), "loclocaldef");
		assertEquals(res.getTargetTaskResult("localmodres"), "locmod");

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "modifiedbaseinputabc");
		assertEquals(res.getTargetTaskResult("fixedres"), "modifiedfixed");
		assertEquals(res.getTargetTaskResult("defres"), "modifieddefval");
		assertEquals(res.getTargetTaskResult("localres"), "loclocaldef");
		assertEquals(res.getTargetTaskResult("localmodres"), "locmod");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());

		input = "123";
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "modifiedbaseinput123");
		assertEquals(res.getTargetTaskResult("fixedres"), "modifiedfixed");
		assertEquals(res.getTargetTaskResult("defres"), "modifieddefval");
		assertEquals(res.getTargetTaskResult("localres"), "loclocaldef");
		assertEquals(res.getTargetTaskResult("localmodres"), "locmod");

//		input = null;
//		res = runTask("build");
//		assertEquals(res.getTaskResult("result"), "modifiedbaseinputxyz");
	}

}
