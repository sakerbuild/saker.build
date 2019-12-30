package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SubscriptChangeBugTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath bfpath = PATH_WORKING_DIRECTORY.resolve("saker.build");

		System.out.println("run 1");
		runScriptTask("test3");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("{field=CONTENT}"));

		System.out.println("run 2");
		files.putFile(bfpath, files.getAllBytes(bfpath).toString().replace("#add", "[nonexistent]#add"));
		//should fail
		assertException(Exception.class, () -> runScriptTask("test3"));
		assertEmpty(getMetric().getAllPrintedTaskLines());

		System.out.println("run 3");
		//should fail again
		assertException(Exception.class, () -> runScriptTask("test3"));
		assertEmpty(getMetric().getAllPrintedTaskLines());
	}
}
