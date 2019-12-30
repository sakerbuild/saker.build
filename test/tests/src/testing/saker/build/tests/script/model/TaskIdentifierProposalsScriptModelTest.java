package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class TaskIdentifierProposalsScriptModelTest extends ScriptModelTestCase {
	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, 0).assertPresent("example.task()", "example.task-q1()", "example.task-q2()",
				"example.task-q1-q2()", "other.task()", "other.task-q1()", "other.task-qx()", "include()", "abort()",
				"global()", "static()", "var()");

		assertProposals(model, endIndexOf(filedata, "exam;") - 1)
				.assertPresent("example.task()", "example.task-q1()", "example.task-q2()", "example.task-q1-q2()")
				.assertNotPresent("other.task()", "include()");
		assertProposals(model, endIndexOf(filedata, "exam()") - 2)
				.assertPresent("example.task()", "example.task-q1()", "example.task-q2()", "example.task-q1-q2()")
				.assertNotPresent("other.task()", "include()");
		assertProposals(model, indexOf(filedata, "exam-q1()") + 4)
				.assertPresent("example.task-q1()", "example.task-q1-q2()", "example.task-q2()", "example.task()")
				.assertNotPresent("other.task()", "include()");

		assertProposals(model, endIndexOf(filedata, "cu()") - 2).assertPresent("custom.task()");

		assertProposals(model, endIndexOf(filedata, "proposal.num;") - 1).assertPresent("proposal.numone.task()",
				"proposal.numtwo.task()");
		assertProposals(model, indexOf(filedata, "proposal.numone.task()") + 3).assertPresent("proposal.numone.task()",
				"proposal.numtwo.task()");
	}
}
