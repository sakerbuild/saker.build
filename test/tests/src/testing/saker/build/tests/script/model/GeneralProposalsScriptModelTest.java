package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class GeneralProposalsScriptModelTest extends ScriptModelTestCase {
	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "EnumParam1: ")).assertPresentOrder("FIRST", "FOURTH", "SECOND",
				"THIRD");
		assertProposals(model, endIndexOf(filedata, "EnumParam1: F")).assertPresentOrder("FIRST", "FOURTH")
				.assertNotPresent("SECOND", "THIRD");
		assertProposals(model, endIndexOf(filedata, "EnumField5: ")).assertPresentOrder("FIRST", "FOURTH", "SECOND",
				"THIRD");
		assertProposals(model, endIndexOf(filedata, "EnumField5: ,"))
				.assertPresentOrder("Field1", "Field2", "ListField4", "MapField3").assertNotPresent("EnumField5");

		assertProposals(model, endIndexOf(filedata, "$taskres[")).assertPresentDisplayType("RetField1", T_FIELD)
				.assertNotPresent("RetField2");
		assertProposals(model, endIndexOf(filedata, "$taskres[][")).assertPresentDisplayType("RetField2", T_FIELD)
				.assertNotPresent("RetField1");

		assertProposals(model, endIndexOf(filedata, "#maptest\r\n}[")).assertPresentDisplayType("MapField1", T_FIELD)
				.assertPresentDisplayType("MapField2", T_FIELD).assertPresentDisplayType("Inner1", T_LITERAL)
				.assertPresentDisplayType("Inner2", T_LITERAL);
		assertProposals(model, endIndexOf(filedata, "#maptest\r\n}[][")).assertPresentDisplayType("Inner1", T_FIELD)
				.assertPresentDisplayType("Inner2", T_FIELD).assertPresentDisplayType("MapField1", T_LITERAL)
				.assertPresentDisplayType("MapField2", T_LITERAL);

		assertProposals(model, endIndexOf(filedata, "$mpvar[MpField][")).assertPresentDisplayType("Field1", T_FIELD)
				.assertPresentDisplayType("Field2", T_FIELD).assertPresentDisplayType("MapField3", T_FIELD)
				.assertPresentDisplayType("ListField4", T_FIELD).assertPresentDisplayType("EnumField5", T_FIELD);
		assertProposals(model, endIndexOf(filedata, "#mpvarmpfield\r\n$mpvar[")).assertPresentDisplayType("MpField",
				T_FIELD);
		assertProposals(model, endIndexOf(filedata, "$mpvar[][MapField3][")).assertPresentDisplayType("Nest1", T_FIELD);
		assertProposals(model, endIndexOf(filedata, "$mpvar[M]") - 1).assertPresentDisplayType("MpField", T_FIELD);

		assertProposals(model, endIndexOf(filedata, "$directenumvar = ")).assertPresentOrder("FIRST", "FOURTH",
				"SECOND", "THIRD");
		assertProposals(model, endIndexOf(filedata, "$indirectenumvar = ")).assertPresentOrder("FIRST", "FOURTH",
				"SECOND", "THIRD");

		assertProposals(model, indexOf(filedata, "N: n1") + 1).assertPresent("Nest2");
		assertProposals(model, indexOf(filedata, "N: n2") + 1).assertPresent("Nest2");
	}
}
