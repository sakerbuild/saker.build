package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class TaskQualifierProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "example.task-()") - 2).assertPresent("q1", "q2");
		assertProposals(model, endIndexOf(filedata, "example.task-q1-()") - 2).assertPresent("q2")
				.assertNotPresent("q1");
		assertProposals(model, endIndexOf(filedata, "example.task-q()") - 2).assertPresent("q1", "q2");
		assertProposals(model, endIndexOf(filedata, "example.task-x()") - 2).assertNotPresent("q1", "q2");
	}

}
