package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class OperatorPrecedenceTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("doubleassign1"), 10L);
		assertEquals(result.getTargetTaskResult("doubleassign2"), 10L);
		assertEquals(result.getTargetTaskResult("addcalc1"), -10L);
		assertEquals(result.getTargetTaskResult("additions"), 1L - 2 - 3 + 4 - -5 + -6 - 7);
		assertEquals(result.getTargetTaskResult("multadditions"), 1L + 2 * 3 + 4 * 5 * 6 + 7);
		assertEquals(result.getTargetTaskResult("multidivisions"), 100L / 2 * 50 * 10 / 4 / 6 * 3);
		assertEquals(result.getTargetTaskResult("multadddivisions"), 1L * 3 + 4 / 2 - 6 + 9 * -9 / 3 + 99);
		assertEquals(result.getTargetTaskResult("modadddivisions"), 1L % 3 + 4 / 2 - 6 + 9 % -9 / 3 + 99);

		assertEquals(result.getTargetTaskResult("addeq"), 1L + 3 == 4L - 2);
		assertEquals(result.getTargetTaskResult("multeq"), 4L * 3 == 2L * 6);

		assertEquals(result.getTargetTaskResult("addlt"), 1 + 2 < 3 - 4);
		assertEquals(result.getTargetTaskResult("multlt"), 1 * 2 < 3 * 4);
		assertEquals(result.getTargetTaskResult("lteq"), 1 < 2 == 3 < 4);

		assertEquals(result.getTargetTaskResult("sftcmp"), 1L << 2 < 3 << 4);
		assertEquals(result.getTargetTaskResult("sfteq"), 1 << 2 != 3 << 4);
		assertEquals(result.getTargetTaskResult("sftmult"), 1L << 2 * 3 << 2);

		assertEquals(result.getTargetTaskResult("bwa"), 1L & 2L | 3L & 4L);
		assertEquals(result.getTargetTaskResult("bwb"), 1L ^ 2L & 3L | 4L ^ 5L);
		assertEquals(result.getTargetTaskResult("bwc"), 1L | 2L ^ 3L & 4L ^ 5L | 6L);

		assertEquals(result.getTargetTaskResult("ca"), 1 == 1 || true && false);
		assertEquals(result.getTargetTaskResult("cb"), true && false && true);
		assertEquals(result.getTargetTaskResult("cc"), false || true && true || false);
	}

}
