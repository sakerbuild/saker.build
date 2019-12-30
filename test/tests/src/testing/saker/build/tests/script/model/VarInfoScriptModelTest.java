package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class VarInfoScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "in inp")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$input")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$unassigned")),
				setOf("doc_example.task_MapParam1"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "MapParam1: $hop")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$hop = ") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "= $secondhop")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$secondhop = ") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$thirdhop") + 2),
				setOf("doc_example.task_MapParam1"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$taskresin") + 2),
				setOf("doc_example.task_return"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "Field1: $fiel")),
				setOf("doc_example.task_MapParam1_Field1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$field1 = f1") + 2),
				setOf("doc_example.task_MapParam1_Field1"));
	}
}
