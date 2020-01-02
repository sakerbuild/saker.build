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
public class DefaultParamInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "FIRST")),
				setOf("doc_example.task_EnumParam1_enumtype_FIRST", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "SECOND")),
				setOf("doc_example.task_EnumParam1", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1_enumtype_SECOND"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "THIRD")),
				setOf("doc_example.task_EnumParam1_enumtype_THIRD", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "FOURTH")),
				setOf("doc_example.task_EnumParam1_enumtype_FOURTH", "doc_example.task_EnumParam1_enumtype",
						"doc_example.task_EnumParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$InParam3[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$OutParam2[RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
	}

}
