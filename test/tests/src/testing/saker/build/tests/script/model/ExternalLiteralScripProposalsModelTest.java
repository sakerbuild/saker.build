package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ExternalLiteralScripProposalsModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "ExtLiteralParam1: ")).assertPresent("EXT_SIMPLE_LITERAL",
				"EXT_OBJECT_LITERAL");
		assertProposals(model, endIndexOf(filedata, "$usedvar = ")).assertPresent("EXT_SIMPLE_LITERAL",
				"EXT_OBJECT_LITERAL");
		assertProposals(model, endIndexOf(filedata, "Element:")).assertPresent("EXT_SIMPLE_LITERAL",
				"EXT_OBJECT_LITERAL");

		assertProposals(model, endIndexOf(filedata, "ExtLiteralParam1: EXT_S")).assertPresent("EXT_SIMPLE_LITERAL")
				.assertNotPresent("EXT_OBJECT_LITERAL");
		assertProposals(model, endIndexOf(filedata, "$usedvar = EXT_S")).assertPresent("EXT_SIMPLE_LITERAL")
				.assertNotPresent("EXT_OBJECT_LITERAL");
		assertProposals(model, endIndexOf(filedata, "Element: EXT_S")).assertPresent("EXT_SIMPLE_LITERAL")
				.assertNotPresent("EXT_OBJECT_LITERAL");
	}
}
