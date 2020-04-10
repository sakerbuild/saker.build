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
public class ParameterSurroundProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);
		exhaustiveProposalRetrieve(model, filedata);
		
		assertProposals(model, startIndexOf(filedata, "\r\n\tParam1: ")).assertPresentOrder("PathParam1");
		assertProposals(model, endIndexOf(filedata, "Param1: ,")).assertPresentOrder("PathParam1");
		
		assertProposals(model, startIndexOf(filedata, "\r\n\tNCParam1: ")).assertPresentOrder("PathParam1");
		assertProposals(model, endIndexOf(filedata, "NCParam1: x\r\n")).assertPresentOrder("PathParam1");
		
		assertProposals(model, endIndexOf(filedata, "NoCommaEmptyParam1:\r\n")).assertPresentOrder("PathParam1");
		
		assertProposals(model, startIndexOf(filedata, "\tParam1: ")).assertEmpty();
	}

}
