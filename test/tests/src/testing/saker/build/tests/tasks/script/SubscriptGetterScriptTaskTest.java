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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

@SakerTest
public class SubscriptGetterScriptTaskTest extends VariablesMetricEnvironmentTestCase {

	public static class DynamicGetter implements Externalizable {
		private static final long serialVersionUID = 1L;

		public String get(String s) {
			return s + s;
		}

		public int get(int i) {
			return i * 2;
		}

		public String getPresentGetter() {
			return "PG";
		}

		public String directValue() {
			return "DV";
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	//member function order changed
	public static class SwitchDynamicGetter implements Externalizable {
		private static final long serialVersionUID = 1L;

		public int get(int i) {
			return i * 2;
		}

		public String get(String s) {
			return s + s;
		}

		public String getPresentGetter() {
			return "PG";
		}

		public String directValue() {
			return "DV";
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	Object getterObject;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.subscript1", getterObject);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		getterObject = new DynamicGetter();
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("subscript1"), "valval");
		assertEquals(res.getTargetTaskResult("presentgetter"), "PG");
		assertEquals(res.getTargetTaskResult("intsubscript"), 123 * 2);
		assertEquals(res.getTargetTaskResult("directval"), "DV");

		getterObject = new SwitchDynamicGetter();
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("subscript1"), "valval");
		assertEquals(res.getTargetTaskResult("presentgetter"), "PG");
		assertEquals(res.getTargetTaskResult("intsubscript"), 123 * 2);
		assertEquals(res.getTargetTaskResult("directval"), "DV");
	}

}
