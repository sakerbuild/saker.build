package testing.saker.build.tests.tasks.script;

import saker.build.runtime.execution.ExecutionParametersImpl;
import testing.saker.SakerTest;

@SakerTest
public class MultipleDefaultsFileTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "abcdef");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

	@Override
	protected void setupParameters(ExecutionParametersImpl params) {
		super.setupParameters(params);
		setDefaultsFileScriptOption(params, "def1.build;def2.build");
	}

}
