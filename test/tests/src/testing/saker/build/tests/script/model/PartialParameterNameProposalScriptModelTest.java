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
public class PartialParameterNameProposalScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		//other other paremeters that contain the "Para" phrase should also be present, but after all the 
		//proposals that start with the phrase
		assertProposals(model, endIndexOf(filedata, "example.task(Para")).assertPresentOrder("ParameterTest1",
				"ParameterTest2", "SimpleParam1", "SimpleParam2", "BoolParam1", "ExtLiteralParam1", "MapParam1");

		//check that applying it is correct
		assertEquals(
				applyProposal(filedata, requireProposalDisplayString(
						model.getCompletionProposals(endIndexOf(filedata, "example.task(Para")), "SimpleParam1")),
				"example.task(SimpleParam1)");
		assertEquals(
				applyProposal(filedata,
						requireProposalDisplayString(
								model.getCompletionProposals(endIndexOf(filedata, "example.task(Pa")), "SimpleParam1")),
				"example.task(SimpleParam1)");

		assertEquals(
				applyProposal(filedata, requireProposalDisplayString(
						model.getCompletionProposals(endIndexOf(filedata, "example.task(Para")), "ParameterTest1")),
				"example.task(ParameterTest1)");
		assertEquals(
				applyProposal(filedata,
						requireProposalDisplayString(
								model.getCompletionProposals(endIndexOf(filedata, "example.task(Pa")), "ParameterTest1")),
				"example.task(ParameterTest1)");
	}

}
