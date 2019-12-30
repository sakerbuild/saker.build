package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class VarDeduceScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, ": $enumvar") + 5),
				setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1",
						"doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Nest1: n1")),
				setOf("doc_example.task_MapParam1_MapField3_Nest1"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SECOND")), setOf("doc_example.task_EnumParam1",
				"doc_example.task_EnumParam1_enumtype_SECOND", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "EnumSubscript:")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[EnumSubscript]") + 1),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$taskresvar = ") + 1),
				setOf("doc_example.task_return"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[RetField1]") + 1),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$taskretfieldvar = ") + 1),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
	}
}
