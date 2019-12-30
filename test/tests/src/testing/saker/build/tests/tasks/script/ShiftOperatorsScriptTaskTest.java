package testing.saker.build.tests.tasks.script;

import java.math.BigInteger;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ShiftOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("a"), 0xFL << 2L);
		assertEquals(result.getTargetTaskResult("b"), 0xFL << 2L);
		assertEquals(result.getTargetTaskResult("d"), 0xFL << 2 << 2);
		assertEquals(result.getTargetTaskResult("e"), 1L << 10);
		assertEquals(result.getTargetTaskResult("f"), 10L << 10L);
		assertEquals(result.getTargetTaskResult("g"), -5L >> 1);
		assertEquals(result.getTargetTaskResult("extend"), BigInteger.valueOf(1).shiftLeft(80));
		assertEquals(result.getTargetTaskResult("negextend"), BigInteger.valueOf(1).shiftLeft(80));
		assertEquals(result.getTargetTaskResult("tonegshift"), BigInteger.valueOf(1).shiftLeft(63));
		assertEquals(result.getTargetTaskResult("tonegshift2"), 1L << 62);
	}
}
