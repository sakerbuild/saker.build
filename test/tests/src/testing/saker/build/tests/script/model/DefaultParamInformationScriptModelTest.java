package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class DefaultParamInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "SECOND")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1_enumtype_SECOND"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "THIRD")),
				setOf("doc_example.task_EnumParam1_enumtype_THIRD", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "FOURTH")),
				setOf("doc_example.task_EnumParam1_enumtype_FOURTH", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$InParam3[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$OutParam2[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
	}

}
