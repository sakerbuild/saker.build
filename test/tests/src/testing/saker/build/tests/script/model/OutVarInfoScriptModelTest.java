package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class OutVarInfoScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out direc")),
				setOf("doc_example.task_return"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out assig")),
				setOf("doc_example.task_return"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$assig")), setOf("doc_example.task_return"));

	}
}
