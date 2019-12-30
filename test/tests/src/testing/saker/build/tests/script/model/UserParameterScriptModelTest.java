package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class UserParameterScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "exec.user.param(test.par")),
				setOf("doc_exec.user.param_", "Value: p1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "exec.user.param(\"test.par")),
				setOf("doc_exec.user.param_", "Value: p2"));
		assertProposals(model, endIndexOf(filedata, "exec.user.param(")).assertPresentOrder("test.param1",
				"test.param2");
	}

}
