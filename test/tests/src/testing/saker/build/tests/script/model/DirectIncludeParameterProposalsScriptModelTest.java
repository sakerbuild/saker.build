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
public class DirectIncludeParameterProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertProposals(model, endIndexOf(filedata, "first()") - 1).assertPresent("finparam1", "finparam2");
		assertProposals(model, endIndexOf(filedata, "first(fin")).assertPresent("finparam1", "finparam2");
		assertProposals(model, endIndexOf(filedata, "first(fin") - 2).assertPresent("finparam1", "finparam2");
		assertProposals(model, endIndexOf(filedata, "first(finparam1")).assertNotPresent("finparam1")
				.assertNotPresent("finparam2");

		assertProposals(model, endIndexOf(filedata, "first(finparam1: 1)") - 4).assertNotPresent("finparam1")
				.assertNotPresent("finparam2");
		assertProposals(model, endIndexOf(filedata, "first(finparam1: 1)") - 5).assertNotPresent("finparam1")
				.assertPresent("finparam2");
	}

}
