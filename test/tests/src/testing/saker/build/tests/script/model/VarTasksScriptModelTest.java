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
public class VarTasksScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		SakerPath globalbuildfilepath = EnvironmentTestCase.PATH_WORKING_DIRECTORY.resolve("global.build");

		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		String globalfiledata = files.getAllBytes(globalbuildfilepath).toString();

		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		ScriptSyntaxModel globalmodel = environment.getModel(globalbuildfilepath);
		model.createModel(null);
		globalmodel.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);
		exhaustiveScriptAnalysis(globalmodel, globalfiledata);

		assertTrue(getInformationsAtOffset(model, indexOf(filedata, "var(enumvar)") + 5)
				.containsAll(setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1")));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1",
						"doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SECOND")), setOf());

		assertTrue(getInformationsAtOffset(model, indexOf(filedata, "static(statenumvar)") + 10)
				.containsAll(setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1")));

		assertTrue(getInformationsAtOffset(model, indexOf(filedata, "global(globalenumvar)") + 10)
				.containsAll(setOf("doc_example.task_EnumParam1_enumtype", "doc_example.task_EnumParam1")));
		assertEquals(getInformationsAtOffset(globalmodel, indexOf(globalfiledata, "FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1",
						"doc_example.task_EnumParam1_enumtype"));
	}
}
