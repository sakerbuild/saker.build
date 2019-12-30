package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class AddOperatorScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("strlist"), listOf("a", "b", "c"));
		assertEquals(result.getTargetTaskResult("strlistappend"), listOf("a", "b", "c", "d"));
		assertEquals(result.getTargetTaskResult("strlistprepend"), listOf("0", "a", "b", "c"));
		assertEquals(result.getTargetTaskResult("strlistconcat"), listOf("a", "b", "c", "x", "y", "z"));
		assertEquals(result.getTargetTaskResult("directlistconcat"), listOf("a", "b", "1", "2"));

		assertEquals(result.getTargetTaskResult("strlistsecond"), listOf("a", "b", "c", "sa", "sb"));
		assertEquals(result.getTargetTaskResult("listsecond"), listOf("1", "2", "sa", "sb"));

		assertEquals(result.getTargetTaskResult("addition"), 4L);
		assertEquals(result.getTargetTaskResult("floatadd"), 1.2d + 3.4d);
		assertEquals(result.getTargetTaskResult("floatintadd"), 1L + 2.3d);
		assertEquals(result.getTargetTaskResult("intsecondadd"), 1L + 10L);
		assertEquals(result.getTargetTaskResult("secondsecondadd"), 10L + 10L);
	}

}
