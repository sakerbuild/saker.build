package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class DuplicateEnumProposalScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		System.out.println("DuplicateEnumProposalScriptModelTest.runTest() "
				+ model.getCompletionProposals(endIndexOf(filedata, "dup.enum.paramed(")));

		assertProposals(model, endIndexOf(filedata, "dup.enum.paramed(")).assertPresentFrequency(1, "ENUMVAL")
				.assertProposalDoc("ENUMVAL", "doc_dup.enum.paramed_entype1_ENUMVAL")
				.assertProposalDoc("ENUMVAL", "doc_dup.enum.paramed_entype2_ENUMVAL");
	}

}
