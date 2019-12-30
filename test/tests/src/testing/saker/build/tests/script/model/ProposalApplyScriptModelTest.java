package testing.saker.build.tests.script.model;

import java.nio.charset.StandardCharsets;
import java.util.List;

import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;

@SakerTest
public class ProposalApplyScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void initFileProvider() throws Exception {
		super.initFileProvider();
		files.putFile(DEFAULT_BUILD_FILE, "");
		files.putFile(DEFAULT_BUILD_FILE.getParent().resolve("file1.txt"), "");
		files.putFile(DEFAULT_BUILD_FILE.getParent().resolve("file2.txt"), "");
		files.putFile(DEFAULT_BUILD_FILE.getParent().resolve("dir1/file3.txt"), "");
		files.putFile(DEFAULT_BUILD_FILE.getParent().resolve("dir1/file4.txt"), "");
		files.putFile(DEFAULT_BUILD_FILE.getParent().resolve("dir2/file5.txt"), "");

		files.putFile(DEFAULT_BUILD_FILE.getParent().resolve("ufile\u12341.txt"), "");
	}

	@Override
	protected void runTest() throws Throwable {
		testIncludeBuildTarget();
		testSimpleVar();
		testBaseVar();
		testFile();
		testStringFile();

		testEmpty();
		testWhitespaceOnly();
		testCommentOnly();

		testInvalidTaskName();

		testParameterInsert();
	}

	private void testParameterInsert() throws Exception {
		System.out.println("ProposalApplyScriptModelTest.testParameterInsert()");
		List<? extends ScriptCompletionProposal> proposals;
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		{
			String data = "example.task(  )";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
			//between the spaces
			proposals = model.getCompletionProposals(data.length() - 2);
			assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "SimpleParam1")),
					"example.task( SimpleParam1 )");
		}
		
		{
			String data = "example.task( first ,  )";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
			//between the spaces
			proposals = model.getCompletionProposals(data.length() - 2);
			assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "SimpleParam1")),
					"example.task( first , SimpleParam1 )");
		}
	}

	private void testInvalidTaskName() throws Exception {
		System.out.println("ProposalApplyScriptModelTest.testInvalidTaskName()");
		List<? extends ScriptCompletionProposal> proposals;
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "example.";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "example.task()")), "example.task()");
	}

	private void testCommentOnly() throws Exception {
		System.out.println("ProposalApplyScriptModelTest.testCommentOnly()");
		List<? extends ScriptCompletionProposal> proposals;
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "#c\n";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "abort()")), "#c\nabort()");
		proposals = model.getCompletionProposals(0);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "abort()")), "abort()#c\n");
	}

	private void testWhitespaceOnly() throws Exception {
		System.out.println("ProposalApplyScriptModelTest.testWhitespaceOnly()");
		//to check that there are some proposals in case of an empty file 
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "    ";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(1);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "abort()")), " abort()   ");
	}

	private void testEmpty() throws Exception {
		System.out.println("ProposalApplyScriptModelTest.testEmpty()");
		//to check that there are some proposals in case of an empty file 
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "abort()")), "abort()");
	}

	private void testStringFile() throws Exception {
		System.out.println("ProposalApplyScriptModelTest.testStringFile()");
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		List<? extends ScriptCompletionProposal> proposals;

		String data = "\"fi\"";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));

		//after ", before fi" 
		proposals = model.getCompletionProposals(1);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file1.txt\"")), "\"file1.txt\"");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file2.txt\"")), "\"file2.txt\"");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"ufile\u12341.txt\"")),
				"\"ufile\u12341.txt\"");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"dir2/file5.txt\"")),
				"\"dir2/file5.txt\"");

		//after "f, before i" 
		proposals = model.getCompletionProposals(2);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file1.txt\"")), "\"file1.txt\"");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file2.txt\"")), "\"file2.txt\"");
		new ProposalAssertion(proposals).assertNotPresent("\"dir2/file5.txt\"");

		//after "fi, before " 
		proposals = model.getCompletionProposals(3);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file1.txt\"")), "\"file1.txt\"");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file2.txt\"")), "\"file2.txt\"");
		new ProposalAssertion(proposals).assertNotPresent("\"dir2/file5.txt\"");

		//after "fi"
		proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file1.txt\"")), "\"file1.txt\"");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "\"file2.txt\"")), "\"file2.txt\"");
		new ProposalAssertion(proposals).assertNotPresent("\"dir2/file5.txt\"");
	}

	private void testFile() throws Exception {
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		List<? extends ScriptCompletionProposal> proposals;

		String data = "fi";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "file1.txt")), "file1.txt");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "file2.txt")), "file2.txt");

		//after f, before i 
		proposals = model.getCompletionProposals(1);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "file1.txt")), "file1.txt");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "file2.txt")), "file2.txt");
	}

	private void testSimpleVar() throws Exception {
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "$var1\n$var2\n$";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "$var1")), data + "var1");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "$var2")), data + "var2");
	}

	private void testBaseVar() throws Exception {
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "$var1\n$var2\n$vxr\n$va";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(data.length());
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "$var1")), data + "r1");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "$var2")), data + "r2");
		new ProposalAssertion(proposals).assertNotPresent("$vxr");

		//after $v, before a
		proposals = model.getCompletionProposals(data.length() - 1);
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "$var1")), data + "r1");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "$var2")), data + "r2");
	}

	private void testIncludeBuildTarget() throws Exception {
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		String data = "include()\nbuild {}";
		model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(endIndexOf(data, "include("));
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "Target")),
				"include(Target)\nbuild {}");
		assertEquals(applyProposal(data, requireProposalDisplayString(proposals, "build")), "include(build)\nbuild {}");
	}

}
