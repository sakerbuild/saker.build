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

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.subscript1", new DynamicGetter());
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("subscript1"), "valval");
		assertEquals(res.getTargetTaskResult("presentgetter"), "PG");
		assertEquals(res.getTargetTaskResult("intsubscript"), 123 * 2);
	}

}
