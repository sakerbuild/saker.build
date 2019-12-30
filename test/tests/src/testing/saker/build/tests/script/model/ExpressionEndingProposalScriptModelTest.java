package testing.saker.build.tests.script.model;

import java.util.List;

import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;

@SakerTest
public class ExpressionEndingProposalScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void initFileProvider() throws Exception {
		super.initFileProvider();
		files.putFile(DEFAULT_BUILD_FILE, "");
	}

	@Override
	protected void runTest() throws Throwable {
		List<? extends ScriptCompletionProposal> proposals;
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		{
			String data = "include()\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include();";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include();\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include()\r\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include()\r";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include()\r\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length() - 1);
			assertEmpty(proposals);
		}
		{
			String data = "include()";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertEmpty(proposals);
		}
	}

}
