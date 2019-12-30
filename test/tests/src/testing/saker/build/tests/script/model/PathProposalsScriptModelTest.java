package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class PathProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "PathParam1: ")).assertPresentOrder("dir/", "file.txt",
				"saker.build");
		assertProposals(model, endIndexOf(filedata, "FileParam1: ")).assertPresentOrder("file.txt", "saker.build",
				"dir/");
		assertProposals(model, endIndexOf(filedata, "DirectoryParam1: ")).assertPresentOrder("dir/", "file.txt",
				"saker.build");
	}

}
