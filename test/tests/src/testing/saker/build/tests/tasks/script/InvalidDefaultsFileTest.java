package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;

@SakerTest
public class InvalidDefaultsFileTest extends DefaultsFileTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		setDefaultsFileScriptOption(parameters, "conflictinfile.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));

		setDefaultsFileScriptOption(parameters, "conflicting1.build;conflicting2.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));

		setDefaultsFileScriptOption(parameters, "recurring.build");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), "abc");

		setDefaultsFileScriptOption(parameters, "forbuiltin.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));

		setDefaultsFileScriptOption(parameters, "nonliteral.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));

		setDefaultsFileScriptOption(parameters, "nontoplevel.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));

		setDefaultsFileScriptOption(parameters, "withbuildtarget.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));

		setDefaultsFileScriptOption(parameters, null);
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask(PATH_WORKING_DIRECTORY.resolve("nondefaultsfile.build")));
		
		setDefaultsFileScriptOption(parameters, "nested.build");
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build"));
		
		setDefaultsFileScriptOption(parameters, "recursive.build");
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("value"), null);
	}

}
