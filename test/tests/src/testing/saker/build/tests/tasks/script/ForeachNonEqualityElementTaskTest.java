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
