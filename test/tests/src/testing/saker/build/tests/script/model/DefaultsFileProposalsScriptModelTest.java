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
public class DefaultsFileProposalsScriptModelTest extends ScriptModelTestCase {
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

		assertProposals(defaultsmodel, 0).assertPresent("defaults()");
		assertProposals(model, 0).assertNotPresent("defaults()");

		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults()") - 1)
				.assertPresentWithInformation("example.task", "doc_example.task").assertPresent("unnamed.paramed");
		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(exam)") - 1)
				.assertPresentWithInformation("example.task", "doc_example.task").assertNotPresent("unnamed.paramed");
		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults([exam])") - 2)
				.assertPresentWithInformation("example.task", "doc_example.task").assertNotPresent("unnamed.paramed");

		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(\"exam\")") - 2)
				.assertPresentWithInformation("\"example.task\"", "doc_example.task")
				.assertNotPresent("\"unnamed.paramed\"");
		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults([\"exam\"])") - 3)
				.assertPresentWithInformation("\"example.task\"", "doc_example.task")
				.assertNotPresent("\"unnamed.paramed\"");

		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(example.task, EnumParam1: )") - 1)
				.assertPresentWithInformation("FIRST", "doc_example.task_EnumParam1_enumtype_FIRST");
		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults([example.task], EnumParam1: )") - 1)
				.assertPresentWithInformation("FIRST", "doc_example.task_EnumParam1_enumtype_FIRST");

		assertProposals(defaultsmodel,
				endIndexOf(defaultsfiledata, "defaults([example.task, example.task-q1], EnumParam1: )") - 1)
						.assertPresentWithInformation("FIRST", "doc_example.task_EnumParam1_enumtype_FIRST")
						.assertPresentWithInformation("FIRST", "doc_example.task-q1_EnumParam1_enumtype_FIRST");

		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults(example.task, )") - 1)
				.assertPresentWithInformation("EnumParam1", "doc_example.task_EnumParam1");
		assertProposals(defaultsmodel, endIndexOf(defaultsfiledata, "defaults([example.task, example.task-q1], )") - 1)
				.assertPresentWithInformation("EnumParam1", "doc_example.task_EnumParam1")
				.assertPresentWithInformation("EnumParam1", "doc_example.task-q1_EnumParam1");
	}
}
