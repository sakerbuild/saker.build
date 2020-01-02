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
public class TaskTokenInformationsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertHasInformation(model, indexOf(filedata, "example.task"), "doc_example.task");
		assertHasInformation(model, indexOf(filedata, "example.task-q1"), "doc_example.task-q1");
		assertHasInformation(model, indexOf(filedata, "example.task-q1-q2"), "doc_example.task-q1-q2");
		assertHasInformation(model, indexOf(filedata, "example.task-q2"), "doc_example.task-q2");
		assertHasInformation(model, indexOf(filedata, "other.task"), "doc_other.task");
		assertHasInformation(model, indexOf(filedata, "other.task-q1"), "doc_other.task-q1");
		assertHasInformation(model, indexOf(filedata, "other.task-qx"), "doc_other.task-qx");
		assertNoInformation(model, indexOf(filedata, "unknown.task"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "example.task-{$var}()")),
				setOf("doc_example.task", "doc_example.task-q1", "doc_example.task-q1-q2", "doc_example.task-q2"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "example.task-q1-{$var}()")),
				setOf("doc_example.task-q1", "doc_example.task-q1-q2"));
	}

}
