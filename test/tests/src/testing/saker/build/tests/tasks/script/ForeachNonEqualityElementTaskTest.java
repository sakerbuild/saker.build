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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.content.SerializableContentDescriptor;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

@SakerTest
public class ForeachNonEqualityElementTaskTest extends VariablesMetricEnvironmentTestCase {
	//bug test for foreach iterable not recognizing change
	
	private List<Object> iterValue;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.iterable", iterValue);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		iterValue = Collections.singletonList(new SerializableContentDescriptor("first"));
		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf(iterValue.stream().map(Object::toString).toArray()));

		iterValue = Collections.singletonList(new SerializableContentDescriptor("second"));
		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf(iterValue.stream().map(Object::toString).toArray()));
	}

}
