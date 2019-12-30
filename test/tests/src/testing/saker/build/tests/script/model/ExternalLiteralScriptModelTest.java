package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ExternalLiteralScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "EXT_SIMPLE_LITERAL")), setOf());
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "EXT_OBJECT_LITERAL")), setOf());

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ExtLiteralParam1: EXT_SIMPLE_LITERAL")),
				setOf("doc_EXT_SIMPLE_LITERAL", "doc_example.task_ExtLiteralParam1_type",
						"doc_example.task_ExtLiteralParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ExtLiteralParam1: EXT_OBJECT_LITERAL")),
				setOf("doc_EXT_OBJECT_LITERAL", "doc_example.task_ExtLiteralParam1_type",
						"doc_example.task_ExtLiteralParam1"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$usedvar = EXT_SIMPLE_LITERAL")),
				setOf("doc_EXT_SIMPLE_LITERAL", "doc_example.task_ExtLiteralParam1_type",
						"doc_example.task_ExtLiteralParam1"));
	}

}
