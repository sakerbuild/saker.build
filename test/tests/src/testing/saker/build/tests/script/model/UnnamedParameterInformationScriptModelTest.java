package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class UnnamedParameterInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "(firstparam)") + 1),
				setOf("doc_unnamed.paramed_"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "(aliasparam)") + 1),
				setOf("doc_alias.paramed.withgeneric_"));

		System.out.println("UnnamedParameterInformationScriptModelTest.runTest() LAST");
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "Alias: al")), setOf("doc_alias.paramed.withgeneric_"));
	}

}
