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
public class DefaultsFileInformationScriptModelTest extends ScriptModelTestCase {
	public static final SakerPath DEFAULTS_BUILD_FILE = EnvironmentTestCase.PATH_WORKING_DIRECTORY
			.resolve("defaults.build");

	@Override
	protected void runTest() throws Throwable {
		String defaultsfiledata = files.getAllBytes(DEFAULTS_BUILD_FILE).toString();
		ScriptSyntaxModel defaultsmodel = environment.getModel(DEFAULTS_BUILD_FILE);
		defaultsmodel.createModel(null);

		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(defaultsmodel, defaultsfiledata);

		exhaustiveScriptAnalysis(model, filedata);

		//the information for the defaults() task should mention itself
		//test for snippets from the documentation
		assertAnyInformationContains(defaultsmodel, endIndexOf(defaultsfiledata, "defaults()") - 3, "defaults()");
		assertAnyInformationContains(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(exam)") - 2,
				"default parameters");

		assertHasInformation(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(example.task,") - 2,
				"doc_example.task");

		assertHasInformation(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(example.task, EnumParam1: )") - 3,
				"doc_example.task_EnumParam1");
		assertHasInformation(defaultsmodel, endIndexOf(defaultsfiledata, "defaults([example.task], EnumParam1: )") - 3,
				"doc_example.task_EnumParam1");

		assertHasInformation(defaultsmodel,
				endIndexOf(defaultsfiledata, "defaults([example.task, example.task-q1], EnumParam1: )") - 3,
				"doc_example.task_EnumParam1", "doc_example.task-q1_EnumParam1");
	}
}
