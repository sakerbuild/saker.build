package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class SubscriptInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetField2")), setOf(
				"doc_example.task_return_RetField1_RetField2", "doc_example.task_return_RetField1_RetField2_type"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetValue:")), setOf("doc_example.task_return"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetValue1:")),
				setOf("doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetValue2:")),
				setOf("doc_example.task_return_RetField1_RetField2_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[RetValue1]") + 1),
				setOf("doc_example.task_return_RetField1_type"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$mapp1 = ") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$mapp1[Field1]") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$mapp1[Fie")),
				setOf("doc_example.task_MapParam1_Field1"));
	}
}
