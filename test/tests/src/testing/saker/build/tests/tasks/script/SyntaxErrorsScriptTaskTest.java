package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SyntaxErrorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("normal");
		BuildTaskExecutionResult res;

		SakerPath mainbuildfile = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);

		res = environment.run(mainbuildfile, "missingparen", parameters, project);
		ScriptTestUtils.assertHasScriptTrace(res.getPositionedExceptionView(), mainbuildfile, 2, 1, 2);

		res = environment.run(mainbuildfile, "missingsubsc", parameters, project);
		ScriptTestUtils.assertHasScriptTrace(res.getPositionedExceptionView(), mainbuildfile, 6, 6, 0);

		res = environment.run(mainbuildfile, "missingif", parameters, project);
		ScriptTestUtils.assertHasScriptTrace(res.getPositionedExceptionView(), mainbuildfile, 9, 4, 1);

	}

}
