package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class IncludeParameterProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "include(first,")).assertPresent("finparam1", "finparam2");
		assertProposals(model, endIndexOf(filedata, "include(first, fin")).assertPresent("finparam1", "finparam2");
		assertProposals(model, endIndexOf(filedata, "include(first, fin") - 2).assertPresent("finparam1", "finparam2");
		assertProposals(model, endIndexOf(filedata, "include(first, finparam1")).assertPresent("finparam1")
				.assertNotPresent("finparam2");

		assertProposals(model, endIndexOf(filedata, "include(first, finparam1: 1)") - 4).assertPresent("finparam1")
				.assertNotPresent("finparam2");
		assertProposals(model, endIndexOf(filedata, "include(first, finparam1: 1)") - 5).assertPresent("finparam1",
				"finparam2");
	}

}
