/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package testing.saker.build.tests.script.model;

import saker.build.file.path.SakerPath;
import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class VarProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		SakerPath globalbuildfilepath = EnvironmentTestCase.PATH_WORKING_DIRECTORY.resolve("global.build");

		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();

		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		ScriptSyntaxModel globalmodel = environment.getModel(globalbuildfilepath);
		model.createModel(null);
		globalmodel.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveTokenInformationRetrieve(globalmodel);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "$;") - 1).assertPresent("$Unassigned", "$MapVar", "$ListVar",
				"$LiteralVar", "$TaskVar");
		assertProposals(model, endIndexOf(filedata, "$L;") - 1).assertPresent("$ListVar", "$LiteralVar")
				.assertNotPresent("$Unassigned", "$MapVar", "$L");
		assertProposals(model, endIndexOf(filedata, "$Li;") - 2).assertPresent("$ListVar", "$LiteralVar", "$Li")
				.assertNotPresent("$Unassigned", "$MapVar", "$L");
		assertProposals(model, endIndexOf(filedata, "$T;") - 1).assertPresent("$TaskVar").assertNotPresent("$T");

		assertProposals(model, endIndexOf(filedata, "var(Li)") - 1).assertPresentDisplayType("ListVar", T_VARIABLE)
				.assertPresentDisplayType("LiteralVar", T_VARIABLE);
		assertProposals(model, endIndexOf(filedata, "global(gl)") - 1).assertPresent("globalenumvar", "globalnumvar")
				.assertNotPresent("aglobalvar");
		assertProposals(model, endIndexOf(filedata, "global()") - 1).assertPresent("globalenumvar", "globalnumvar",
				"aglobalvar");

		assertProposals(model, endIndexOf(filedata, "static(enumvar) = ")).assertPresent("FIRST", "SECOND", "THIRD",
				"FOURTH");
		assertProposals(model, endIndexOf(filedata, "static(enumvar) = F")).assertPresent("FIRST", "FOURTH")
				.assertNotPresent("SECOND", "THIRD");
		assertProposals(model, endIndexOf(filedata, "static(enumvar) = FIRST")).assertPresent()
				.assertNotPresent("SECOND", "THIRD", "FIRST", "FOURTH");
	}

}
