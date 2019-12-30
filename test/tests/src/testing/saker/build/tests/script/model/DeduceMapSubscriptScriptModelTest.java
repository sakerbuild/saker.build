package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class DeduceMapSubscriptScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[Element]") + 1),
				setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Element:")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "FIRST")), setOf("doc_example.task_EnumParam1",
				"doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[Retrieval]") + 1),
				setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Retrieval:")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SECOND")), setOf("doc_example.task_EnumParam1",
				"doc_example.task_EnumParam1_enumtype_SECOND", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[Nested]") + 1), setOf());

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Vared:")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[Vared]") + 1),
				setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "THIRD")), setOf("doc_example.task_EnumParam1",
				"doc_example.task_EnumParam1_enumtype_THIRD", "doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "FOURTH")), setOf("doc_example.task_EnumParam1",
				"doc_example.task_EnumParam1_enumtype_FOURTH", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "NONEXISTENT")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "VARNAMED")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype"));
	}
}
