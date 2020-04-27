package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class AddOpInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "MapParam1: { Fi")),
				setOf("doc_example.task_MapParam1_Field1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "MapParam1: $var + { Fiel")),
				setOf("doc_example.task_MapParam1_Field1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ListEnumParam1: [FIR")),
				setOf("doc_example.task_ListEnumParam1_list_enumtype",
						"doc_example.task_ListEnumParam1_list_enumtype_FIRST"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ListEnumParam1: $var + [FI")),
				setOf("doc_example.task_ListEnumParam1_list_enumtype",
						"doc_example.task_ListEnumParam1_list_enumtype_FIRST"));
	}

}
