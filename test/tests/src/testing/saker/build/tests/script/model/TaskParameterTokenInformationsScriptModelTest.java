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
public class TaskParameterTokenInformationsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: sp1")),
				setOf("doc_example.task_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: sp2")),
				setOf("doc_example.task_SimpleParam2"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: sp1sp2")),
				setOf("doc_example.task_SimpleParam2"));
		assertEquals(
				getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: example.task(SimpleParam2: sp1sp2)")),
				setOf("doc_example.task_SimpleParam1"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Unknown: un")), setOf());

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: q1sp1")),
				setOf("doc_example.task-q1_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: q1sp2")),
				setOf("doc_example.task-q1_SimpleParam2"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: q1q2sp1")),
				setOf("doc_example.task-q1-q2_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: q1q2sp2")),
				setOf("doc_example.task-q1-q2_SimpleParam2"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: q1varsp1")),
				setOf("doc_example.task-q1_SimpleParam1", "doc_example.task-q1-q2_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: q1varsp2")),
				setOf("doc_example.task-q1_SimpleParam2", "doc_example.task-q1-q2_SimpleParam2"));
	}

}
