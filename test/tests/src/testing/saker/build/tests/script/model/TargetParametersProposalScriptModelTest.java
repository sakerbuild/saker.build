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

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class TargetParametersProposalScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);

		assertProposals(model, endIndexOf(filedata, "in var")).assertPresentOrder("varfirst", "varfourth", "varsecond",
				"varthird");
		assertProposals(model, endIndexOf(filedata, "out var")).assertPresentOrder("varfirst", "varfourth", "varsecond",
				"varthird");

		assertProposals(model, endIndexOf(filedata, "in varf")).assertPresentOrder("varfirst", "varfourth")
				.assertNotPresent("varsecond", "varthird");

		assertProposals(model, endIndexOf(filedata, "in varf") - 1).assertPresentOrder("varfirst", "varfourth",
				"varsecond", "varthird");

		assertProposals(model, endIndexOf(filedata, "in  "))
				.assertPresentOrder("varfirst", "varfourth", "varsecond", "varthird").assertNotPresent("item", "local");
		assertProposals(model, endIndexOf(filedata, "out  "))
				.assertPresentOrder("varfirst", "varfourth", "varsecond", "varthird").assertNotPresent("item", "local");

		assertProposals(model, startIndexOf(filedata, "in  someth") + 3).assertEmpty();

		assertProposals(model, endIndexOf(filedata, "in inputparam = ")).assertNotEmpty();
	}

}
