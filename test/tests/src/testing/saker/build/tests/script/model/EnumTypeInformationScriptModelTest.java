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
public class EnumTypeInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1",
						"doc_example.task_EnumParam1_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SECOND")),
				setOf("doc_example.task_MapParam1_EnumField5_enumtype_SECOND", "doc_example.task_MapParam1_EnumField5",
						"doc_example.task_MapParam1_EnumField5_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "EnumField5")),
				setOf("doc_example.task_MapParam1_EnumField5", "doc_example.task_MapParam1_EnumField5_enumtype"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "ListEnumParam1")),
				setOf("doc_example.task_ListEnumParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "THIRD")),
				setOf("doc_example.task_ListEnumParam1_list_enumtype_THIRD", "doc_example.task_ListEnumParam1_list",
						"doc_example.task_ListEnumParam1", "doc_example.task_ListEnumParam1_list_enumtype"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "FOURTH")),
				setOf("doc_example.task_ListEnumParam1_list_enumtype_FOURTH",
						"doc_example.task_ListEnumParam1_list_enumtype"));
	}
}
