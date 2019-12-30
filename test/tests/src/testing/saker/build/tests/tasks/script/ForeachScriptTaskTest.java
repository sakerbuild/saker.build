package testing.saker.build.tests.tasks.script;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ForeachScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("strlist"), listOf("a", "b", "c"));
		assertEquals(result.getTargetTaskResult("strlistappend"), listOf("a", "b", "c", "d"));
		assertEquals(result.getTargetTaskResult("strlistprepend"), listOf("0", "a", "b", "c"));
		assertEquals(result.getTargetTaskResult("strlistconcat"), listOf("a", "b", "c", "x", "y", "z"));
		assertEquals(result.getTargetTaskResult("directlistconcat"), listOf("a", "b", "1", "2"));

		assertEquals(result.getTargetTaskResult("strlistforeach"), "axbxcx");
		assertEquals(result.getTargetTaskResult("strlistappendforeach"), "abcd");
		assertEquals(result.getTargetTaskResult("strlistprependforeach"), "0abc");
		assertEquals(result.getTargetTaskResult("strlistconcatforeach"), "abcxyz");
		assertEquals(result.getTargetTaskResult("directlistconcatforeach"), "ab12");

		assertEquals(result.getTargetTaskResult("strlistsecond"), listOf("a", "b", "c", "sa", "sb"));
		assertEquals(result.getTargetTaskResult("listsecond"), listOf("1", "2", "sa", "sb"));

		assertEquals(result.getTargetTaskResult("strlistsecondforeach"), "abcsasb");
		assertEquals(result.getTargetTaskResult("listsecondforeach"), "12sasb");

		assertEquals(result.getTargetTaskResult("globallistout"), "123");

		assertEquals(result.getTargetTaskResult("list1"), listOf(1L, 2L));
		assertEquals(result.getTargetTaskResult("list2"), listOf(1L, 2L, 2L, 3L));

		assertEquals(result.getTargetTaskResult("repeating"), listOf(2L, 2L, 3L));
		assertEquals(result.getTargetTaskResult("indexes"), listOf(0L, 1L, 2L));

		assertEquals(result.getTargetTaskResult("maplist"), listOf("K1", "v1", "K2", "v2"));
		assertEquals(result.getTargetTaskResult("foreachpluslist"), listOf(2L, 4L, 3L));

		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask(SakerPath.valueOf("loopvarlocalnameconflict.build")));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("recurringkeys"));

		runScriptTask("successful", SakerPath.valueOf("nestednames.build"));
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("loopvars", SakerPath.valueOf("nestednames.build")));
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("looplocal", SakerPath.valueOf("nestednames.build")));
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("locals", SakerPath.valueOf("nestednames.build")));
		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("outerlocal", SakerPath.valueOf("nestednames.build")));
	}

}
