package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class MapFieldProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "#nokey\r\n\t\t")).assertPresent("Field1", "Field2", "MapField3",
				"ListField4", "EnumField5");
		assertProposals(model, endIndexOf(filedata, "F:") - 1).assertPresent("Field1", "Field2")
				.assertNotPresent("MapField3", "ListField4", "EnumField5");
		assertProposals(model, indexOf(filedata, "F:")).assertPresent("Field1", "Field2", "MapField3", "ListField4",
				"EnumField5", "F");
		assertProposals(model, endIndexOf(filedata, "$mapvar = {")).assertPresent("Field1", "Field2", "MapField3",
				"ListField4", "EnumField5");
		assertProposals(model, endIndexOf(filedata, "Element: {")).assertPresent("Field1", "Field2", "MapField3",
				"ListField4", "EnumField5");
	}

}
