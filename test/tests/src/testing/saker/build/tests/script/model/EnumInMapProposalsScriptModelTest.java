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

/**
 * Tests a bug where invoking the completion proposals inside of a map structure gave proposals for the map receiver
 * type if it was an enum.
 */
@SakerTest
public class EnumInMapProposalsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {

		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();

		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		System.out.println("EnumInMapProposalsScriptModelTest.runTest() done exhaustive");

		assertProposals(model, endIndexOf(filedata, "example.task(EnumParam1: F")).assertPresent("FIRST", "FOURTH",
				"FIFTH");

		System.out.println("EnumInMapProposalsScriptModelTest.runTest() check ordered");
		//enums should come before variables for these proposals
		assertProposals(model, endIndexOf(filedata, "example.task(EnumParam1: )") - 1).assertPresentOrder("FIRST",
				"$variable");

		System.out.println("EnumInMapProposalsScriptModelTest.runTest() check bug");

		// we're invoking proposal in a map (in the map_key specifically), don't list the enums from the EnumParam1 type 
		assertProposals(model, endIndexOf(filedata, "example.task(EnumParam1: { ")).assertNotPresent("FIRST", "SECOND",
				"THIRD", "FOURTH", "FIFTH", "SIXTH", "SEVENTH");
		assertProposals(model, endIndexOf(filedata, "example.task(EnumParam1: { F")).assertNotPresent("FIRST", "SECOND",
				"THIRD", "FOURTH", "FIFTH", "SIXTH", "SEVENTH");
	}

}
