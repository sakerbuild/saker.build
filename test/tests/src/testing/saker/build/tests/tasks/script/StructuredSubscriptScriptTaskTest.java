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

import java.util.Map;
import java.util.TreeMap;

import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

/**
 * Tests that the subscript operator doesn't incorrectly retrieve the fields of {@link StructuredTaskResult} instances,
 * but get the fields of their proper results.
 */
@SakerTest
public class StructuredSubscriptScriptTaskTest extends VariablesMetricEnvironmentTestCase {

	private Object getterObject;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.subscript1", getterObject);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		// should not return the task id values via StructuredObjectTaskResult.getTaskIdentifier, 
		//   but the proper value
		res = runScriptTask("object_task_identifier");
		assertEquals(res.getTargetTaskResult("outsvar"), 123L);
		assertEquals(res.getTargetTaskResult("outgvar"), 456L);
	}

}
