package testing.saker.build.tests.script.model;

import java.io.IOException;

import saker.build.file.path.SakerPath;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class SubscriptInfoBugTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		testFirst();
		testSecond();
	}

	private void testSecond() throws IOException, ScriptParsingFailedException, AssertionError {
		SakerPath path = EnvironmentTestCase.PATH_WORKING_DIRECTORY.resolve("bug2.build");
		String filedata = files.getAllBytes(path).toString();
		ScriptSyntaxModel model = environment.getModel(path);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out opt")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$opt")), setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$opt[MapField3")),
				setOf("doc_example.task_MapParam1_MapField3"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$opt[MapField3][Nest1")),
				setOf("doc_example.task_MapParam1_MapField3_Nest1"));
	}

	private void testFirst() throws IOException, ScriptParsingFailedException, AssertionError {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ListMapParam1: $config[fie")),
				setOf("doc_example.task_ListMapParam1", "doc_example.task_ListMapParam1_listmap",
						"doc_example.task_ListMapParam1_listmap_elemtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "field: ") - 3),
				setOf("doc_example.task_ListMapParam1", "doc_example.task_ListMapParam1_listmap",
						"doc_example.task_ListMapParam1_listmap_elemtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "include(getopts)[optio")),
				setOf("doc_example.task_ListMapParam1_listmap_elemtype"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$optio")),
				setOf("doc_example.task_ListMapParam1_listmap_elemtype"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$options[Fiel")),
				setOf("doc_example.task_ListMapParam1_Field1"));
	}

}
