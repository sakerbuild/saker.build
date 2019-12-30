package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class MapFieldInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertFieldInfos(filedata, model, "Field1", "f1");
		assertFieldInfos(filedata, model, "Field2", "f2");
	}

	private static void assertFieldInfos(String filedata, ScriptSyntaxModel model, String fieldname, String fid)
			throws AssertionError {
		//start of region, end of region, inside region
		assertFieldInfosWithIndex(filedata, model, fieldname, fid, 0);
		assertFieldInfosWithIndex(filedata, model, fieldname, fid, fieldname.length());
		assertFieldInfosWithIndex(filedata, model, fieldname, fid, fieldname.length() / 2);
	}

	private static void assertFieldInfosWithIndex(String filedata, ScriptSyntaxModel model, String fieldname,
			String fid, int idx) throws AssertionError {
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, fieldname + ": " + fid) + idx),
				setOf("doc_example.task_MapParam1_" + fieldname));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, fieldname + ": q1" + fid) + idx),
				setOf("doc_example.task-q1_MapParam1_" + fieldname));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, fieldname + ": q1q2" + fid) + idx),
				setOf("doc_example.task-q1-q2_MapParam1_" + fieldname));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, fieldname + ": q1var" + fid) + idx),
				setOf("doc_example.task-q1_MapParam1_" + fieldname, "doc_example.task-q1-q2_MapParam1_" + fieldname));
	}

}
