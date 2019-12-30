package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ForeachInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$item[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$local[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$assignedlocal[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "foreach $en in [FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "foreach $en in [FIRST, SECOND")),
				setOf("doc_example.task_EnumParam1_enumtype_SECOND", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$envar = THIRD")),
				setOf("doc_example.task_EnumParam1_enumtype_THIRD", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "foreach $key, $val in { Key: FOURTH")),
				setOf("doc_example.task_EnumParam1_enumtype_FOURTH", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "foreach $key, $val in { FIFTH")),
				setOf("doc_example.task_EnumParam1_enumtype_FIFTH", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$item in [example.task()]") + 2),
				setOf("doc_example.task_return"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$val in { Key: FOURTH }") + 2),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
	}

}
