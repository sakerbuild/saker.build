package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ScriptDocScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "build(")), setOf("doc_build"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "in inp")), setOf("doc_in"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out outp")), setOf("doc_out"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$input")), setOf("doc_in"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "output")), setOf("doc_out"));

		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(bui")).contains("doc_build"));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(build, inp")).contains("doc_in"));
		System.out.println("ScriptDocScriptModelTest.runTest() "
				+ getInformationsAtOffset(model, endIndexOf(filedata, "include(build, input: as")));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(build, input: as")).contains("doc_in"));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(build, input: asd)[out"))
				.contains("doc_out"));
		assertTrue(getInformationsAtOffset(model, indexOf(filedata, "ut = include(build, input: asd)[output]"))
				.contains("doc_out"));
	}

}
