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
public class NestedTypesInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertFieldInfos(filedata, model, "Nest1", "n1", "doc_example.task_MapParam1_MapField3_");
		assertFieldInfos(filedata, model, "Nest2", "n2", "doc_example.task_MapParam1_ListField4_");
		assertFieldInfos(filedata, model, "Nest2", "n3", "doc_example.task_MapParam1_ListField4_");
	}

	private static void assertFieldInfos(String filedata, ScriptSyntaxModel model, String fieldname, String fid,
			String prefix) throws AssertionError {
		//start of region, end of region, inside region
		assertFieldInfosWithIndex(filedata, model, fieldname, fid, 0, prefix);
		assertFieldInfosWithIndex(filedata, model, fieldname, fid, fieldname.length(), prefix);
		assertFieldInfosWithIndex(filedata, model, fieldname, fid, fieldname.length() / 2, prefix);
	}

	private static void assertFieldInfosWithIndex(String filedata, ScriptSyntaxModel model, String fieldname,
			String fid, int idx, String prefix) throws AssertionError {
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, fieldname + ": " + fid) + idx),
				setOf(prefix + fieldname));
	}
}
