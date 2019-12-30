package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class AbortScriptTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		String msg;

		msg = assertTaskException("saker.build.internal.scripting.language.exc.BuildAbortedException",
				() -> runScriptTask("build")).getMessage();
		assertEquals(msg, "AbortMessage");
		assertNotEmpty(getMetric().getRunTaskIdFactories());

		//make sure that when the build is rerun, the abort task is not reinvoked, as no deltas for it have changed
		msg = assertTaskException("saker.build.internal.scripting.language.exc.BuildAbortedException",
				() -> runScriptTask("build")).getMessage();
		assertEquals(msg, "AbortMessage");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}
}
