package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ParameterInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$InParam1[Enum")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$InParam2[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$OutEnum = SECOND")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1_enumtype_SECOND"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out OutDefEnum = THIRD")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype_THIRD",
						"doc_example.task_EnumParam1_enumtype"));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(enuser, InEnum: FOURTH"))
				.contains("doc_example.task_EnumParam1_enumtype_FOURTH"));

	}

}
