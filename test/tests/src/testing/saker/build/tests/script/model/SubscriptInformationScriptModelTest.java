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
public class SubscriptInformationScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetField1")),
				setOf("doc_example.task_return_RetField1", "doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetField2")), setOf(
				"doc_example.task_return_RetField1_RetField2", "doc_example.task_return_RetField1_RetField2_type"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetValue:")), setOf("doc_example.task_return"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetValue1:")),
				setOf("doc_example.task_return_RetField1_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "RetValue2:")),
				setOf("doc_example.task_return_RetField1_RetField2_type"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "[RetValue1]") + 1),
				setOf("doc_example.task_return_RetField1_type"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$mapp1 = ") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$mapp1[Field1]") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$mapp1[Fie")),
				setOf("doc_example.task_MapParam1_Field1"));
	}
}
