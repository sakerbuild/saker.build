package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ConflictingTargetDefaultValueScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("dualdefault"));

		runScriptTask("indefault");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("outdefault"));
	}

}
