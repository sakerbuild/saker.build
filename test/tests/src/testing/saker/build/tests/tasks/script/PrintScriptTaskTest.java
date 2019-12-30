package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class PrintScriptTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("hello"));

		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("hello"));

		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"),
				files.getAllBytes(PATH_WORKING_DIRECTORY.resolve("saker.build")).toString().replace("hello", "mod"));
		runScriptTask("build");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("mod"));
	}
}
