package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class OnlyDeclaredParameterProposalsModelTest extends ScriptModelTestCase {
	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "customtask()") - 1).assertPresent("CustomParam1", "CustomParam2",
				"CusParam3");
		assertProposals(model, endIndexOf(filedata, "customtask(Cus)") - 1).assertPresent("CustomParam1",
				"CustomParam2", "CusParam3");
		assertProposals(model, endIndexOf(filedata, "customtask(Cust)") - 1).assertPresent("CustomParam1",
				"CustomParam2");

		assertProposals(model, endIndexOf(filedata, "customtask(x, )") - 1).assertPresent("CustomParam1",
				"CustomParam2", "CusParam3");
		assertProposals(model, endIndexOf(filedata, "customtask(x, Cus)") - 1).assertPresent("CustomParam1",
				"CustomParam2", "CusParam3");
		assertProposals(model, endIndexOf(filedata, "customtask(x, Cust)") - 1).assertPresent("CustomParam1",
				"CustomParam2");
	}
}
