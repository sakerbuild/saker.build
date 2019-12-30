package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class IncludeBasedReceiverTypeScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Field1")),
				setOf("doc_example.task_MapParam1_Field1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "Field1: f")),
				setOf("doc_example.task_MapParam1_Field1"));
		
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out out")),
				setOf("doc_example.task_MapParam1"));
	}

}
