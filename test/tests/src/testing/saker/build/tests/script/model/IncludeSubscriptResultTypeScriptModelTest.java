package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class IncludeSubscriptResultTypeScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out outinit")),
				setOf("doc_example.task_return", "oi"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out outvar")),
				setOf("doc_example.task_return", "ov"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$oinit = include(first)[out")),
				setOf("doc_example.task_return", "oi"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$ovar = include(first)[out")),
				setOf("doc_example.task_return", "ov"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$oin")),
				setOf("doc_example.task_return", "oi"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$ova")),
				setOf("doc_example.task_return", "ov"));
	}

}
