package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class InputParameterScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "in inp")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "Field1:") - 2),
				setOf("doc_example.task_MapParam1_Field1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "Field1: f1") - 1),
				setOf("doc_example.task_MapParam1_Field1"));
		
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "in uninit")),
				setOf("doc_example.task_MapParam1"));
	}

}
