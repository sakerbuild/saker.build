package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MapSubscriptScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("build"));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("conflict"));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("directconflict", PATH_WORKING_DIRECTORY.resolve("parsedirectconflict.build")));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("nokeyunused"));
		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("nokeyout"));
	}

}
