package testing.saker.build.tests.tasks.script;

import java.util.List;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

@SakerTest
public class ControlFlowScriptTaskTest extends VariablesMetricEnvironmentTestCase {
	private static final SakerPath BUILD_FILE_PATH = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);

	private boolean ifInput = true;
	private List<Object> foreachList;

	@Override
	protected Map<String, ?> getTaskVariables() {
		return TestUtils.<String, Object>treeMapBuilder().put("test.IfInput", ifInput)
				.put("test.ForeachList", foreachList).build();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		ifInput = true;
		res = runScriptTask("iftest");
		assertEquals(res.getTargetTaskResult("out"), ifInput);

		ifInput = false;
		res = runScriptTask("iftest");
		assertEquals(res.getTargetTaskResult("out"), ifInput);

		ifInput = true;
		res = runScriptTask("ternarytest");
		assertEquals(res.getTargetTaskResult("out"), ifInput);

		ifInput = false;
		res = runScriptTask("ternarytest");
		assertEquals(res.getTargetTaskResult("out"), ifInput);

		res = runScriptTask("constantiftest");
		assertEquals(res.getTargetTaskResult("out"), "constantiftrue");

		files.putFile(BUILD_FILE_PATH,
				files.getAllBytes(BUILD_FILE_PATH).toString().replace("else { $out = constantiffalse; }", ""));
		res = runScriptTask("constantiftest");
		assertEquals(res.getTargetTaskResult("out"), "constantiftrue");
		assertEquals(getMetric().getRunTaskIdResults().size(), 1);

		res = runScriptTask("constantternarytest");
		assertEquals(res.getTargetTaskResult("out"), "constantternarytrue");

		files.putFile(BUILD_FILE_PATH, files.getAllBytes(BUILD_FILE_PATH).toString().replace("constantternaryfalse",
				"constantternarytruemodified"));
		res = runScriptTask("constantternarytest");
		assertEquals(res.getTargetTaskResult("out"), "constantternarytrue");
		assertEquals(getMetric().getRunTaskIdResults().size(), 1);

		res = runScriptTask("foreachlist");
		assertEquals(res.getTargetTaskResult("out1"), 1L);
		assertEquals(res.getTargetTaskResult("out2"), 2L);

		res = runScriptTask("foreachvarlist");
		assertEquals(res.getTargetTaskResult("out1"), 1L);
		assertEquals(res.getTargetTaskResult("out2"), 2L);

		foreachList = ImmutableUtils.asUnmodifiableArrayList("1", 2);
		res = runScriptTask("foreachinputlist");
		assertEquals(res.getTargetTaskResult("out1"), "1");
		assertEquals(res.getTargetTaskResult("out2"), 2);

		res = runScriptTask("foreachlistresult");
		assertEquals(res.getTargetTaskResult("out"), ImmutableUtils.asUnmodifiableArrayList(2L, 3L));

		res = runScriptTask("foreachconcatresult");
		assertEquals(res.getTargetTaskResult("out"), "12");

		res = runScriptTask("foreachmapresult");
		assertEquals(res.getTargetTaskResult("out"),
				TestUtils.hashMapBuilder().put("key1", 1L).put("key2", 2L).build());
	}

}
