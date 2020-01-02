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
public class VarInfoScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "in inp")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$input")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$unassigned")),
				setOf("doc_example.task_MapParam1"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "MapParam1: $hop")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$hop = ") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "= $secondhop")),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$secondhop = ") + 2),
				setOf("doc_example.task_MapParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$thirdhop") + 2),
				setOf("doc_example.task_MapParam1"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$taskresin") + 2),
				setOf("doc_example.task_return"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "Field1: $fiel")),
				setOf("doc_example.task_MapParam1_Field1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "$field1 = f1") + 2),
				setOf("doc_example.task_MapParam1_Field1"));
	}
}
