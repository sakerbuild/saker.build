package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ForeachResultInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$res1 = foreach $item in [FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$res1 = foreach $item in [FIRST, SECOND")),
				setOf("doc_example.task_EnumParam1_enumtype_SECOND", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$res2 = foreach $item in [FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$res2 = foreach $item in [FIRST, SECOND")),
				setOf("doc_example.task_EnumParam1_enumtype_SECOND", "doc_example.task_EnumParam1_enumtype"));
	}

}
