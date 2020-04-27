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
public class TaskIdentifierProposalsScriptModelTest extends ScriptModelTestCase {
	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertProposals(model, 0).assertPresent("example.task()", "example.task-q1()", "example.task-q2()",
				"example.task-q1-q2()", "other.task()", "other.task-q1()", "other.task-qx()", "include()", "abort()",
				"global()", "static()", "var()");

		assertProposals(model, endIndexOf(filedata, "exam;") - 1)
				.assertPresent("example.task()", "example.task-q1()", "example.task-q2()", "example.task-q1-q2()")
				.assertNotPresent("other.task()", "include()");
		assertProposals(model, endIndexOf(filedata, "exam()") - 2)
				.assertPresent("example.task()", "example.task-q1()", "example.task-q2()", "example.task-q1-q2()")
				.assertNotPresent("other.task()", "include()");
		assertProposals(model, indexOf(filedata, "exam-q1()") + 4)
				.assertPresent("example.task-q1()", "example.task-q1-q2()", "example.task-q2()", "example.task()")
				.assertNotPresent("other.task()", "include()");

		assertProposals(model, endIndexOf(filedata, "cu()") - 2).assertPresent("custom.task()");

		assertProposals(model, endIndexOf(filedata, "proposal.num;") - 1).assertPresent("proposal.numone.task()",
				"proposal.numtwo.task()");
		assertProposals(model, indexOf(filedata, "proposal.numone.task()") + 3).assertPresent("proposal.numone.task()",
				"proposal.numtwo.task()");
	}
}
